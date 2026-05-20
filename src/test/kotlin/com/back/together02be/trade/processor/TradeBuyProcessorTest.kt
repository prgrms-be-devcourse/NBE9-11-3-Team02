package com.back.together02be.trade.processor

import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.entity.UserStock
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.global.idempotency.IdempotencyKey
import com.back.together02be.global.idempotency.IdempotencyKeyRepository
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.entity.Stock
import com.back.together02be.stock.entity.StockMarket
import com.back.together02be.stock.repository.StockRepository
import com.back.together02be.stock.service.RealTimeStockPriceStore
import com.back.together02be.trade.dto.BuyReq
import com.back.together02be.trade.entity.Trade
import com.back.together02be.trade.repository.TradeRepository
import com.back.together02be.users.entity.Users
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.*
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.util.ReflectionTestUtils
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@ExtendWith(MockitoExtension::class)
class TradeBuyProcessorTest {

    @Mock lateinit var stockPriceStore: RealTimeStockPriceStore
    @Mock lateinit var userAccountRepository: UserAccountRepository
    @Mock lateinit var userStockRepository: UserStockRepository
    @Mock lateinit var stockRepository: StockRepository
    @Mock lateinit var tradeRepository: TradeRepository
    @Mock lateinit var idempotencyKeyRepository: IdempotencyKeyRepository
    @Mock lateinit var objectMapper: ObjectMapper
    @Mock lateinit var eventPublisher: ApplicationEventPublisher

    @InjectMocks
    lateinit var tradeBuyProcessor: TradeBuyProcessor

    private lateinit var user: Users
    private lateinit var stock: Stock
    private lateinit var account: UserAccount
    private lateinit var freshKey: IdempotencyKey

    @BeforeEach
    fun setUp() {
        user = Users("testuser", "password", "테스트유저")
        account = UserAccount(user, 0L, 50_000_000L)
        stock = Stock("005930", "삼성전자", StockMarket.KOSPI)
        ReflectionTestUtils.setField(stock, "id", 1L)

        freshKey = IdempotencyKey("test-key", 1L)
        ReflectionTestUtils.setField(freshKey, "createdAt", LocalDateTime.now())

        Mockito.lenient().`when`(idempotencyKeyRepository.findByIdempotencyKey(anyString())).thenReturn(freshKey)
        Mockito.lenient().`when`(stockRepository.findById(1L)).thenReturn(Optional.of(stock))
        Mockito.lenient().`when`(userAccountRepository.decreaseDepositIfSufficient(anyLong(), anyLong())).thenReturn(1)
        Mockito.lenient().`when`(userAccountRepository.findByUsersIdWithLock(1L)).thenReturn(Optional.of(account))
        Mockito.lenient().`when`(tradeRepository.save(any(Trade::class.java))).thenAnswer { invocation ->
            val trade = invocation.getArgument<Trade>(0)
            ReflectionTestUtils.setField(trade, "id", 1L)
            trade
        }
        // K2 컴파일러는 Spring Data @NonNull save() 리턴값에 null-check를 생성.
        // stub 없으면 Mockito가 null 리턴 → NPE.
        Mockito.lenient().`when`(userStockRepository.save(any(UserStock::class.java))).thenAnswer { invocation ->
            invocation.getArgument<UserStock>(0)
        }
        // K2는 writeValueAsString() 리턴값에도 null-check를 생성.
        Mockito.lenient().`when`(objectMapper.writeValueAsString(any())).thenReturn("{}")
    }

    private fun mockPrice(stockCode: String, price: Long): RealtimeStockPrice =
        RealtimeStockPrice(
            stockCode = stockCode,
            price = price.toString(),
            changeSign = "",
            change = "",
            changeRate = "",
            tradeTime = null
        )

    @Test
    @DisplayName("정상 매수 (신규 보유종목) — 잔고 차감 쿼리 호출, 거래 내역·UserStock 저장")
    fun 정상_매수_신규_보유종목() {
        val price = 70_000L
        val quantity = 10L
        val amount = price * quantity

        Mockito.`when`(stockPriceStore.get("005930")).thenReturn(mockPrice("005930", price))
        Mockito.`when`(userStockRepository.findByUsersIdAndStockId(1L, 1L)).thenReturn(Optional.empty())

        val response = tradeBuyProcessor.processBuy(1L, "test-key", BuyReq(1L, quantity, 70_000L))

        assertThat(response.price).isEqualTo(price)
        assertThat(response.quantity).isEqualTo(quantity)
        assertThat(response.amount).isEqualTo(amount)

        Mockito.verify(userAccountRepository).decreaseDepositIfSufficient(1L, amount)
        Mockito.verify(userStockRepository).save(any(UserStock::class.java))
        Mockito.verify(tradeRepository).save(any(Trade::class.java))
    }

    @Test
    @DisplayName("추가 매수 — 평균매입가 재계산 검증")
    fun 추가_매수_평균매입가_재계산() {
        val existingQty = 10L
        val existingAvgPrice = 60_000L
        val newPrice = 70_000L
        val newQty = 10L
        val expectedAvgPrice = (existingQty * existingAvgPrice + newQty * newPrice) / (existingQty + newQty)

        val existing = UserStock(user, stock, existingQty, existingAvgPrice)

        Mockito.`when`(stockPriceStore.get("005930")).thenReturn(mockPrice("005930", newPrice))
        Mockito.`when`(userStockRepository.findByUsersIdAndStockId(1L, 1L)).thenReturn(Optional.of(existing))

        tradeBuyProcessor.processBuy(1L, "test-key", BuyReq(1L, newQty, 70_000L))

        assertThat(existing.quantity).isEqualTo(existingQty + newQty)
        assertThat(existing.averagePrice).isEqualTo(expectedAvgPrice)
        Mockito.verify(userStockRepository, Mockito.never()).save(any())
    }

    @Test
    @DisplayName("잔고 부족 — @Modifying이 0 반환 시 예외 발생")
    fun 잔고_부족_예외() {
        val price = 70_000L
        val quantity = 1_000L
        val amount = price * quantity

        Mockito.`when`(stockPriceStore.get("005930")).thenReturn(mockPrice("005930", price))
        Mockito.`when`(userAccountRepository.decreaseDepositIfSufficient(1L, amount)).thenReturn(0)
        Mockito.`when`(userAccountRepository.findByUsersId(1L)).thenReturn(Optional.of(account))

        assertThatThrownBy { tradeBuyProcessor.processBuy(1L, "test-key", BuyReq(1L, quantity, 70_000L)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("잔고가 부족합니다")
    }

    @Test
    @DisplayName("슬리피지 초과 — 현재가가 예상가 대비 2% 초과 시 예외")
    fun 슬리피지_초과_예외() {
        val expectedPrice = 70_000L
        val currentPrice = 72_500L // 70000 * 1.02 = 71400, 72500 > 71400

        Mockito.`when`(stockPriceStore.get("005930")).thenReturn(mockPrice("005930", currentPrice))

        assertThatThrownBy { tradeBuyProcessor.processBuy(1L, "test-key", BuyReq(1L, 10L, expectedPrice)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("가격이 너무 올랐습니다")
    }

    @Test
    @DisplayName("슬리피지 허용 — 현재가가 예상가 대비 2% 이하 상승 시 정상 매수")
    fun 슬리피지_허용_범위_내_매수() {
        val expectedPrice = 70_000L
        val currentPrice = 71_000L // 70000 * 1.02 = 71400, 71000 <= 71400

        Mockito.`when`(stockPriceStore.get("005930")).thenReturn(mockPrice("005930", currentPrice))
        Mockito.`when`(userStockRepository.findByUsersIdAndStockId(1L, 1L)).thenReturn(Optional.empty())

        val response = tradeBuyProcessor.processBuy(1L, "test-key", BuyReq(1L, 10L, expectedPrice))

        assertThat(response.price).isEqualTo(currentPrice)
    }

    @Test
    @DisplayName("가격 신선도 초과 — 웹소켓 체결시각 10초 초과 시 체결 거부")
    fun 가격_신선도_초과_예외() {
        val staleTime = LocalDateTime.now().minusSeconds(11)
            .format(DateTimeFormatter.ofPattern("HHmmss"))

        whenever(stockPriceStore.get("005930")).thenReturn(
            RealtimeStockPrice(
                stockCode = "005930",
                price = "70000",
                changeSign = "",
                change = "",
                changeRate = "",
                tradeTime = staleTime
            )
        )

        assertThatThrownBy { tradeBuyProcessor.processBuy(1L, "test-key", BuyReq(1L, 10L, 70_000L)) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("가격 정보가 오래되었습니다")
    }

    @Test
    @DisplayName("가격 신선도 허용 — 웹소켓 체결시각 10초 이내 시 정상 매수")
    fun 가격_신선도_허용_정상_매수() {
        val freshTime = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("HHmmss"))

        whenever(stockPriceStore.get("005930")).thenReturn(
            RealtimeStockPrice(
                stockCode = "005930",
                price = "70000",
                changeSign = "",
                change = "",
                changeRate = "",
                tradeTime = freshTime
            )
        )

        Mockito.`when`(userStockRepository.findByUsersIdAndStockId(1L, 1L)).thenReturn(Optional.empty())

        val response = tradeBuyProcessor.processBuy(1L, "test-key", BuyReq(1L, 10L, 70_000L))

        assertThat(response.price).isEqualTo(70_000L)
    }

    @Test
    @DisplayName("현재가 없음 — 예외 발생")
    fun 현재가_없을때_예외() {
        Mockito.`when`(stockPriceStore.get("005930")).thenReturn(null)

        assertThatThrownBy { tradeBuyProcessor.processBuy(1L, "test-key", BuyReq(1L, 10L, 70_000L)) }
            .isInstanceOf(EntityNotFoundException::class.java)
            .hasMessageContaining("현재가 정보")
    }
}