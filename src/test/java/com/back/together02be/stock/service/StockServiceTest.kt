package com.back.together02be.stock.service

import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.entity.Stock
import com.back.together02be.stock.entity.StockMarket
import com.back.together02be.stock.repository.StockRepository
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.*
import org.mockito.BDDMockito.given
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.stubbing.Answer
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
@DisplayName("StockService 테스트")
internal class StockServiceTest {
    // Mock
    @Mock
    lateinit var stockRepository: StockRepository

    @Mock
    lateinit var rtStockPriceStore: RealTimeStockPriceStore

    // SUT
    @InjectMocks
    lateinit var stockService: StockService

    // 헬퍼
    private fun givenStockExists(stockCode: String) {
        BDDMockito.given(stockRepository.findByStockCode(stockCode))
            .willReturn(Mockito.mock(Stock::class.java))
    }

    @Test
    @DisplayName("유효한 종목코드로 조회하면 StockPriceRes를 반환한다")
    fun getStockPrice_success() {
        // given
        val stockCode = "005930"
        val stock = Stock(stockCode, "삼성전자", StockMarket.KOSPI)
        ReflectionTestUtils.setField(stock, "id", 1L) // BaseEntity의 id 주입

        Mockito.`when`(stockRepository.findByStockCode(stockCode))
            .thenReturn(stock)

        // when
        val result = stockService.getStockPrice(stockCode)

        // then
        Assertions.assertThat(result.stockId).isEqualTo(1L)
        Assertions.assertThat(result.stockCode).isEqualTo(stockCode)
        Assertions.assertThat(result.stockName).isEqualTo("삼성전자")
    }

    @Test
    @DisplayName("존재하지 않는 종목코드로 조회하면 EntityNotFoundException이 발생한다")
    fun getStockPrice_notFound() {
        // given
        val stockCode = "INVALID"
        Mockito.`when`(stockRepository.findByStockCode(stockCode))
            .thenReturn(null)

        // when & then
        Assertions.assertThatThrownBy({ stockService.getStockPrice(stockCode) })
            .isInstanceOf(EntityNotFoundException::class.java)
            .hasMessageContaining("존재하지 않는 종목코드입니다")
    }

    @Test
    @DisplayName("존재하지 않는 종목코드면 EntityNotFoundException 이 발생하고 SseEmitter 는 생성되지 않는다")
    fun 없는_종목코드_예외() {
        given(stockRepository.findByStockCode(INVALID_CODE))
            .willReturn(null)

        Mockito.mockConstruction(SseEmitter::class.java).use { mocked ->
            Assertions.assertThatThrownBy({
                stockService.createSseEmitter(
                    INVALID_CODE
                )
            })
                .isInstanceOf(EntityNotFoundException::class.java)
                .hasMessageContaining(INVALID_CODE)
            Assertions.assertThat(mocked.constructed()).isEmpty()
        }
    }

    @Test
    @DisplayName("유효한 종목코드면 SseEmitter 를 반환하고 onCompletion·onTimeout 콜백이 등록된다")
    fun 유효한_종목_emitter_반환_및_콜백_등록() {
        givenStockExists(VALID_CODE)
        BDDMockito.given(rtStockPriceStore.get(VALID_CODE)).willReturn(null) // send 호출 방지

        Mockito.mockConstruction(SseEmitter::class.java).use { mocked ->
            val result = stockService.createSseEmitter(VALID_CODE)
            Assertions.assertThat(result).isNotNull()
            val emitter = mocked.constructed().get(0)
            Mockito.verify(emitter, Mockito.atLeastOnce()).onCompletion(
                ArgumentMatchers.any(
                    Runnable::class.java
                )
            )
            Mockito.verify(emitter, Mockito.atLeastOnce())
                .onTimeout(ArgumentMatchers.any(Runnable::class.java))
        }
    }

    @Test
    @DisplayName("emitter 생성 후 스케줄러가 store.get() 을 최소 1회 이상 호출한다")
    @Throws(InterruptedException::class)
    fun 주기_폴링_발생() {
        givenStockExists(VALID_CODE)

        val latch = CountDownLatch(1)
        Mockito.doAnswer(Answer { inv: InvocationOnMock ->
            latch.countDown()
            null
        }).`when`(rtStockPriceStore).get(VALID_CODE)

        Mockito.mockConstruction(SseEmitter::class.java).use { _ ->
            stockService.createSseEmitter(VALID_CODE)
            val polled = latch.await(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            Assertions.assertThat(polled)
                .`as`("스케줄러가 %dms 안에 store.get() 을 호출해야 한다", POLL_TIMEOUT_MS)
                .isTrue()
        }
    }

    @Test
    @DisplayName("store 에 해당 종목 가격이 없으면 emitter.send() 를 호출하지 않는다")
    @Throws(Exception::class)
    fun 가격_null이면_send_미호출() {
        givenStockExists(VALID_CODE)

        // get() 이 null 을 반환하면서 latch 를 카운트다운 → 폴링 완료 시점 포착
        val latch = CountDownLatch(1)
        Mockito.doAnswer(Answer { inv : InvocationOnMock ->
            latch.countDown()
            null // stockPrice == null → send() 분기 미진입
        }).`when`(rtStockPriceStore).get(VALID_CODE)

        Mockito.mockConstruction(SseEmitter::class.java).use { mocked ->
            stockService.createSseEmitter(VALID_CODE)
            latch.await(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            val emitter = mocked.constructed().get(0)
            Mockito.verify(emitter, Mockito.never()).send(ArgumentMatchers.any(Any::class.java))
        }
    }

    @Test
    @DisplayName("store 에 가격이 있으면 emitter.send(stockPrice) 를 호출한다")
    @Throws(Exception::class)
    fun 가격_있으면_send_호출() {
        givenStockExists(VALID_CODE)
        val price = Mockito.mock(RealtimeStockPrice::class.java)

        val getLatch = CountDownLatch(1)
        Mockito.doAnswer(Answer { inv: InvocationOnMock ->
            getLatch.countDown()
            price
        })
            .`when`(rtStockPriceStore).get(VALID_CODE)

        Mockito.mockConstruction(SseEmitter::class.java).use { mocked ->
            stockService.createSseEmitter(VALID_CODE)
            getLatch.await(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            Thread.sleep(200) // get() 과 send() 는 같은 Runnable — 100% 동일 실행에서 연속 호출

            val emitter = mocked.constructed().get(0)
            Mockito.verify(emitter, Mockito.atLeastOnce()).send(price)
        }
    }

    @Test
    @DisplayName("send 중 IOException 이 발생하면 emitter.complete() 를 호출한다")
    @Throws(Exception::class)
    fun IOException_발생시_complete_호출() {
        givenStockExists(VALID_CODE)
        val price = Mockito.mock(RealtimeStockPrice::class.java)
        BDDMockito.given(rtStockPriceStore.get(VALID_CODE)).willReturn(price)

        Mockito.mockConstruction(
            SseEmitter::class.java,
             { emitter: SseEmitter, ctx: MockedConstruction.Context ->
                Mockito.doThrow(IOException("클라이언트 연결 끊김"))
                    .`when`(emitter).send(price)
            }).use { mocked ->  // any() 오버로드 대신 price 인스턴스로 명시
            stockService.createSseEmitter(VALID_CODE)
            Thread.sleep(SSE_INTERVAL_MS * 3) // 첫 폴링(0ms) + IOException 처리 + complete() 여유

            val emitter = mocked.constructed().get(0)
            Mockito.verify(emitter, Mockito.atLeastOnce()).complete()
        }
    }

    // 전체 종목 조회 (ST-01) 테스트
    @Test
    @DisplayName("전체 종목 조회 - 실시간 캐시값이 있으면 현재가와 등락률을 매핑하여 반환한다")
    fun 전체_종목_조회_캐시있음() {
        // given
        val stock = Stock("005930", "삼성전자", StockMarket.KOSPI)
        ReflectionTestUtils.setField(stock, "id", 1L)
        given(stockRepository.findAll()).willReturn(listOf(stock))

        val price = RealtimeStockPrice.builder()
            .stockCode("005930")
            .price("70000")
            .changeRate("2.19")
            .build()
        given(rtStockPriceStore.get("005930")).willReturn(price)

        // when
        val result = stockService.getStocks()

        // then
        Assertions.assertThat(result).hasSize(1)
        Assertions.assertThat(result[0].stockCode).isEqualTo("005930")
        Assertions.assertThat(result[0].currentPrice).isEqualTo(70000L)
        Assertions.assertThat(result[0].changeRate).isEqualTo(2.19)
    }

    @Test
    @DisplayName("전체 종목 조회 - 캐시값이 없거나 파싱에 실패하면 null을 반환한다")
    fun 전체_종목_조회_캐시없음_및_파싱실패() {
        // given
        val stock1 = Stock("000660", "SK하이닉스", StockMarket.KOSPI)
        ReflectionTestUtils.setField(stock1, "id", 2L)
        val stock2 = Stock("035420", "NAVER", StockMarket.KOSPI)
        ReflectionTestUtils.setField(stock2, "id", 3L)

        given(stockRepository.findAll()).willReturn(listOf(stock1, stock2))
        given(rtStockPriceStore.get("000660")).willReturn(null) // 캐시 없음

        val badPrice = RealtimeStockPrice.builder()
            .stockCode("035420").price("abc").changeRate("rate").build()
        given(rtStockPriceStore.get("035420")).willReturn(badPrice) // 숫자 파싱 실패

        // when
        val result = stockService.getStocks()

        // then
        Assertions.assertThat(result).hasSize(2)
        Assertions.assertThat(result[0].currentPrice).isNull()
        Assertions.assertThat(result[0].changeRate).isNull()
        Assertions.assertThat(result[1].currentPrice).isNull()
        Assertions.assertThat(result[1].changeRate).isNull()
    }

    @Test
    @DisplayName("전체 종목 SSE 연결 시 SseEmitter 를 반환하고 콜백이 등록된다")
    fun 전체_종목_SSE_생성_및_콜백_등록() {
        // given & when
        Mockito.mockConstruction(SseEmitter::class.java).use { mocked ->
            val result = stockService.createStockListSseEmitter()
            // then
            Assertions.assertThat(result).isNotNull()
            val emitter = mocked.constructed().get(0)
            Mockito.verify(emitter, Mockito.atLeastOnce()).onCompletion(
                ArgumentMatchers.any(
                    Runnable::class.java
                )
            )
            Mockito.verify(emitter, Mockito.atLeastOnce())
                .onTimeout(ArgumentMatchers.any(Runnable::class.java))
        }
    }

    companion object {
        // 테스트 픽스처
        private const val VALID_CODE = "005930"
        private const val INVALID_CODE = "INVALID"
        private const val POLL_TIMEOUT_MS = 2000L // 폴링 대기 최대치
        private const val SSE_INTERVAL_MS = 500L // 실제 상수와 맞춤
    }
}