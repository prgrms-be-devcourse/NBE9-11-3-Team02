package com.back.together02be.infra.kis

import com.back.together02be.infra.kis.event.WebSocketReconnectedEvent
import com.back.together02be.infra.kis.rest.KisPriceClient
import com.back.together02be.infra.kis.rest.dto.KisPriceRes
import com.back.together02be.infra.kis.websocket.KisWebSocketClient
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.entity.Stock
import com.back.together02be.stock.entity.StockMarket
import com.back.together02be.stock.repository.StockRepository
import com.back.together02be.stock.service.RealTimeStockPriceStore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * StockSubscriptionInitializer 단위 테스트
 *
 * 다루는 흐름:
 *   - initialize(): 앱 시작 시 REST 시딩 + WebSocket 구독
 *   - onWebSocketReconnected(): WS 재연결 이벤트 수신 시 REST 재시딩
 *
 * 전략: 순수 단위 테스트. Spring 컨텍스트 없이 수동 생성.
 * @EventListener + @Async는 테스트 스코프 밖 — 메서드 직접 호출.
 *
 * 주의: seedPricesByRest()와 reseedPricesByRest()에 Thread.sleep(1000)이 종목당 1번 박혀있음.
 * 종목 수를 최소화(1~2개)해서 sleep 총량을 줄임.
 */
internal class StockSubscriptionInitializerTest {
    // Mock
    private lateinit var stockRepository: StockRepository
    private lateinit var kisPriceClient: KisPriceClient
    private lateinit var kisWebSocketClient: KisWebSocketClient
    private lateinit var rtStockPriceStore: RealTimeStockPriceStore

    // SUT
    private lateinit var sut: StockSubscriptionInitializer

    // 테스트 픽스처
    companion object {
        private const val TOKEN = "test-token"
    }

    private fun stock(code: String) = Stock(code, "${code}_name", StockMarket.KOSPI)

    private fun mockPriceRes() = KisPriceRes(
        "0", "MCA00000", "정상처리",
        KisPriceRes.Output("70800", "100", "2", "0.14")
    )

    private fun setReseedInProgress(value: Boolean) {
        val field = StockSubscriptionInitializer::class.java.getDeclaredField("reseedInProgress")
        field.isAccessible = true
        (field.get(sut) as AtomicBoolean).set(value)
    }

    // Lifecycle
    @BeforeEach
    fun setUp() {
        stockRepository = mock()
        kisPriceClient = mock()
        kisWebSocketClient = mock()
        rtStockPriceStore = mock()

        whenever(kisPriceClient.accessToken).thenReturn(TOKEN)

        sut = StockSubscriptionInitializer(
            stockRepository, kisPriceClient, kisWebSocketClient, rtStockPriceStore
        )
    }

    // 테스트 케이스
    @Nested
    @DisplayName("앱 시작 시 초기화 동작")
    inner class InitializationTest {
        @Test
        @DisplayName("종목이 없으면 조기 리턴 — 후속 REST 호출 없음")
        fun initialize_whenNoStocks_returnsEarly() {
            whenever(stockRepository.findAll()).thenReturn(listOf())

            sut.initialize()

            verify(kisPriceClient, never()).accessToken
            verify(kisPriceClient, never()).getCurrentPrice(any(), any())
            verify(kisWebSocketClient, never()).subscribe(any())
            verify(rtStockPriceStore, never()).putIfAbsent(any(), any())
        }

        @Test
        @DisplayName("종목이 있으면 REST 시딩 후 WebSocket 구독이 호출된다 (1종목, sleep 1초)")
        fun initialize_withOneStock_seedsAndSubscribes() {
            val s = stock("005930")
            whenever(stockRepository.findAll()).thenReturn(listOf(s))
            whenever(kisPriceClient.getCurrentPrice(TOKEN, "005930")).thenReturn(mockPriceRes())

            sut.initialize()

            verify(kisPriceClient).getCurrentPrice(TOKEN, "005930")
            verify(rtStockPriceStore).putIfAbsent(eq("005930"), any<RealtimeStockPrice>())
            verify(kisWebSocketClient).subscribe("005930")
        }

        @Test
        @DisplayName("시딩 루프 전체 완료 후 구독 루프가 시작된다 — InOrder로 순서 박제 (2종목, sleep 2초)")
        fun initialize_seedingCompletesBeforeSubscribing() {
            val s1 = stock("005930")
            val s2 = stock("000660")
            whenever(stockRepository.findAll()).thenReturn(listOf(s1, s2))
            whenever(kisPriceClient.getCurrentPrice(eq(TOKEN), any())).thenReturn(mockPriceRes())

            sut.initialize()

            // 시딩(putIfAbsent) → 구독(subscribe) 순서 검증
            val inOrder = inOrder(rtStockPriceStore, kisWebSocketClient)
            inOrder.verify(rtStockPriceStore, times(2)).putIfAbsent(any(), any())
            inOrder.verify(kisWebSocketClient, times(2)).subscribe(any())
        }

        @Test
        @DisplayName("REST 시딩 실패한 종목이 있어도 나머지 종목은 계속 처리된다 (2종목, sleep 2초)")
        fun seedPrices_whenOneStockFails_othersAreProcessed() {
            val s1 = stock("005930")
            val s2 = stock("000660")
            whenever(stockRepository.findAll()).thenReturn(listOf(s1, s2))

            whenever(kisPriceClient.getCurrentPrice(TOKEN, "005930"))
                .thenThrow(RuntimeException("REST 오류"))
            whenever(kisPriceClient.getCurrentPrice(TOKEN, "000660"))
                .thenReturn(mockPriceRes())

            sut.initialize()

            // 005930은 실패했으므로 putIfAbsent 호출 안 됨
            verify(rtStockPriceStore, never()).putIfAbsent(eq("005930"), any())
            // 000660은 성공
            verify(rtStockPriceStore).putIfAbsent(eq("000660"), any<RealtimeStockPrice>())

            // 구독은 실패 여부와 무관하게 전 종목 시도
            verify(kisWebSocketClient).subscribe("005930")
            verify(kisWebSocketClient).subscribe("000660")
        }

        @Test
        @DisplayName("WebSocket 구독 실패한 종목이 있어도 나머지 종목 구독은 계속 시도된다 (2종목, sleep 2초)")
        fun subscribeAll_whenOneStockFails_othersAreSubscribed() {
            val s1 = stock("005930")
            val s2 = stock("000660")
            whenever(stockRepository.findAll()).thenReturn(listOf(s1, s2))
            whenever(kisPriceClient.getCurrentPrice(eq(TOKEN), any())).thenReturn(mockPriceRes())

            doThrow(RuntimeException("구독 오류")).whenever(kisWebSocketClient).subscribe("005930")

            sut.initialize()

            // 실패해도 000660 구독은 계속 시도
            verify(kisWebSocketClient).subscribe("005930")
            verify(kisWebSocketClient).subscribe("000660")
        }

        @Test
        @DisplayName("캐시 저장은 putIfAbsent로 호출된다 — put은 호출되지 않는다 (1종목, sleep 1초)")
        fun seedPrices_usesPutIfAbsent_notPut() {
            val s = stock("005930")
            whenever(stockRepository.findAll()).thenReturn(listOf(s))
            whenever(kisPriceClient.getCurrentPrice(TOKEN, "005930")).thenReturn(mockPriceRes())

            sut.initialize()

            verify(rtStockPriceStore).putIfAbsent(eq("005930"), any<RealtimeStockPrice>())
            verify(rtStockPriceStore, never()).put(any(), any())
        }
    }

    @Nested
    @DisplayName("WebSocket 재연결 후 재시딩 동작")
    inner class ReseedTest {

        @Test
        @DisplayName("재시딩은 put을 사용한다 — putIfAbsent는 호출되지 않는다 (1종목, sleep 1초)")
        fun reseed_usesPut_notPutIfAbsent() {
            val s = stock("005930")
            whenever(stockRepository.findAll()).thenReturn(listOf(s))
            whenever(kisPriceClient.getCurrentPrice(TOKEN, "005930")).thenReturn(mockPriceRes())

            sut.onWebSocketReconnected(WebSocketReconnectedEvent())

            verify(rtStockPriceStore).put(eq("005930"), any<RealtimeStockPrice>())
            verify(rtStockPriceStore, never()).putIfAbsent(any(), any())
        }

        @Test
        @DisplayName("재시딩 중 일부 종목 실패해도 나머지 종목은 계속 처리된다 (2종목, sleep 2초)")
        fun reseed_whenOneStockFails_othersAreProcessed() {
            val s1 = stock("005930")
            val s2 = stock("000660")
            whenever(stockRepository.findAll()).thenReturn(listOf(s1, s2))
            whenever(kisPriceClient.getCurrentPrice(TOKEN, "005930"))
                .thenThrow(RuntimeException("REST 오류"))
            whenever(kisPriceClient.getCurrentPrice(TOKEN, "000660"))
                .thenReturn(mockPriceRes())

            sut.onWebSocketReconnected(WebSocketReconnectedEvent())

            verify(rtStockPriceStore, never()).put(eq("005930"), any())
            verify(rtStockPriceStore).put(eq("000660"), any<RealtimeStockPrice>())
        }

        @Test
        @DisplayName("이미 재시딩 진행 중이면 두 번째 이벤트는 skip된다 (REST 호출 없음)")
        fun reseed_whenAlreadyInProgress_skipsSecondCall() {
            setReseedInProgress(true)

            sut.onWebSocketReconnected(WebSocketReconnectedEvent())

            verify(stockRepository, never()).findAll()
            verify(kisPriceClient, never()).accessToken
            verify(kisPriceClient, never()).getCurrentPrice(any(), any())
            verify(rtStockPriceStore, never()).put(any(), any())
        }

        @Test
        @DisplayName("재시딩 정상 종료 후 reseedInProgress는 false로 복귀한다 (2호출, sleep 2초)")
        fun reseed_resetsGuardFlagAfterCompletion() {
            val s = stock("005930")
            whenever(stockRepository.findAll()).thenReturn(listOf(s))
            whenever(kisPriceClient.getCurrentPrice(TOKEN, "005930")).thenReturn(mockPriceRes())

            sut.onWebSocketReconnected(WebSocketReconnectedEvent())
            sut.onWebSocketReconnected(WebSocketReconnectedEvent())

            // 두 번 다 정상 진입했으면 findAll이 2번 호출됨
            verify(stockRepository, times(2)).findAll()
        }
    }

}