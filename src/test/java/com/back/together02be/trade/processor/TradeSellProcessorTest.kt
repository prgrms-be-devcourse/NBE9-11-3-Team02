package com.back.together02be.trade.processor

//import org.mockito.BDDMockito.given
import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.entity.UserStock
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.entity.Stock
import com.back.together02be.stock.entity.StockMarket
import com.back.together02be.stock.repository.StockRepository
import com.back.together02be.stock.service.RealTimeStockPriceStore
import com.back.together02be.trade.dto.request.TradeSellReq
import com.back.together02be.trade.entity.Trade
import com.back.together02be.trade.repository.TradeRepository
import com.back.together02be.users.entity.Users
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.util.ReflectionTestUtils
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@ExtendWith(MockitoExtension::class)
class TradeSellProcessorTest {

    private lateinit var tradeSellProcessor: TradeSellProcessor

    @Mock lateinit var stockPriceStore: RealTimeStockPriceStore
    @Mock lateinit var userAccountRepository: UserAccountRepository
    @Mock lateinit var userStockRepository: UserStockRepository
    @Mock lateinit var stockRepository: StockRepository
    @Mock lateinit var tradeRepository: TradeRepository
    @Mock lateinit var clock: Clock

    //private lateinit var marketValidator: MockedStatic<MarketTimeValidator>


    private val fixedClock = Clock.fixed(
        LocalDateTime.of(2024, 1, 15, 10, 0)
            .atZone(ZoneId.of("Asia/Seoul")).toInstant(),
        ZoneId.of("Asia/Seoul")
    )


    @BeforeEach
    fun setUp() {
        tradeSellProcessor = TradeSellProcessor(
            stockPriceStore,
            userAccountRepository,
            userStockRepository,
            stockRepository,
            tradeRepository,
            clock
        )
//        lenient().given(clock.instant()).willReturn(fixedInstant)
//        lenient().given(clock.zone).willReturn(seoulZone)
        lenient().`when`(clock.instant()).thenReturn(fixedClock.instant())
        lenient().`when`(clock.zone).thenReturn(fixedClock.zone)
    }

    @AfterEach
    fun tearDown() {
    }


    // 공통 Mocking 설정을 위한 Helper 메서드
    private fun mockCommonDependencies(stock: Stock, userStock: UserStock, account: UserAccount) {
        given(stockRepository.findById(anyLong())).willReturn(Optional.of(stock))
        given(userStockRepository.findByUsersIdAndStockId(anyLong(), anyLong())).willReturn(Optional.of(userStock))
    }

    @Test
    @DisplayName("t1: 부분 매도 성공")
    fun t1() {
        // given
        val stock = Stock("005930", "삼성전자", StockMarket.KOSPI)
        val dummyUser = Users("username", "password", "nickname")
        val userStock = UserStock(dummyUser, stock, 20L, 10000L)
        val account = UserAccount(dummyUser, 1000000L, 0L)
        mockCommonDependencies(stock, userStock, account)

        val nowTime = "100000"
        given(stockPriceStore.get(stock.stockCode)).willReturn(
            RealtimeStockPrice.builder().price("55000").tradeTime(nowTime).build()
        )
        given(userStockRepository.updateQuantity(anyLong(), anyLong(), anyLong())).willReturn(1)
        given(userAccountRepository.updateDepositAndPurchase(anyLong(), anyLong(), anyLong())).willReturn(1)
        given(userAccountRepository.findByUsersId(anyLong())).willReturn(Optional.of(account))

        val mockTrade = Trade.sell(account.users, stock, 10L, 55000L, 450000L)
        ReflectionTestUtils.setField(mockTrade, "id", 1L)
        given(tradeRepository.save(any(Trade::class.java))).willReturn(mockTrade)

        // when
        val res = tradeSellProcessor.processSell(1L, TradeSellReq(1L, 10L, 10L, 50000L))

        // then
        assertThat(res.quantity).isEqualTo(10L) // 코틀린 프로퍼티 접근 (getter 제거)
        verify(tradeRepository, atLeastOnce()).save(any(Trade::class.java))
    }

    @Test
    @DisplayName("t2: 전량 매도 성공")
    fun t2() {
        // given
        val stock = Stock("005930", "삼성전자", StockMarket.KOSPI)
        val dummyUser = Users("username", "password", "nickname")
        val userStock = UserStock(dummyUser, stock, 20L, 10000L)
        val account = UserAccount(dummyUser, 1000000L, 0L)
        mockCommonDependencies(stock, userStock, account)

        val nowTime = "100000"
        given(stockPriceStore.get(stock.stockCode)).willReturn(
            RealtimeStockPrice.builder().price("55000").tradeTime(nowTime).build()
        )
        given(userStockRepository.updateQuantity(anyLong(), anyLong(), anyLong())).willReturn(1)
        given(userAccountRepository.updateDepositAndPurchase(anyLong(), anyLong(), anyLong())).willReturn(1)
        given(userAccountRepository.findByUsersId(anyLong())).willReturn(Optional.of(account))

        val mockTrade = Trade.sell(account.users, stock, 20L, 55000L, 900000L)
        ReflectionTestUtils.setField(mockTrade, "id", 2L)
        given(tradeRepository.save(any(Trade::class.java))).willReturn(mockTrade)

        // when
        tradeSellProcessor.processSell(1L, TradeSellReq(1L, 10L, 20L, 50000L))

        // then
        verify(userStockRepository).deleteByUserAndStock(1L, 10L)
    }

    @Test
    @DisplayName("t3: 실패 - 가격 변동폭 초과")
    fun t3() {
        // given
        val stock = Stock("005930", "삼성전자", StockMarket.KOSPI)
        val dummyUser = Users("username", "password", "nickname")
        val userStock = UserStock(dummyUser, stock, 20L, 10000L)
        val account = UserAccount(dummyUser, 1000000L, 0L)
        mockCommonDependencies(stock, userStock, account)

        val nowTime = LocalTime.now(fixedClock).format(DateTimeFormatter.ofPattern("HHmmss"))
        given(stockPriceStore.get(stock.stockCode)).willReturn(
            RealtimeStockPrice.builder().price("55000").tradeTime(nowTime).build()
        )

        // when & then (JUnit5 assertThrows 코틀린 스타일 스타일화)
        assertThrows<IllegalStateException> {
            tradeSellProcessor.processSell(1L, TradeSellReq(1L, 10L, 10L, 100000L))
        }
    }

    @Test
    @DisplayName("t4: 실패 - 보유 수량 부족")
    fun t4() {
        // given
        val stock = Stock("005930", "삼성전자", StockMarket.KOSPI)
        val dummyUser = Users("username", "password", "nickname")
        val userStock = UserStock(dummyUser, stock, 20L, 10000L)
        val account = UserAccount(dummyUser, 1000000L, 0L)
        mockCommonDependencies(stock, userStock, account)

        val nowTime = "100000"
        given(stockPriceStore.get(anyString())).willReturn(
            RealtimeStockPrice.builder().price("55000").tradeTime(nowTime).build()
        )
        given(userStockRepository.updateQuantity(anyLong(), anyLong(), anyLong())).willReturn(0)

        // when & then
        assertThrows<IllegalStateException> {
            tradeSellProcessor.processSell(1L, TradeSellReq(1L, 10L, 100L, 10000L))
        }
    }
}