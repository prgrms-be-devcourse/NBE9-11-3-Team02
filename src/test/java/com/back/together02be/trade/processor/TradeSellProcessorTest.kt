package com.back.together02be.trade.processor

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
import com.back.together02be.trade.repository.TradeRepository
import com.back.together02be.trade.util.MarketTimeValidator
import com.back.together02be.users.entity.Users
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

@ExtendWith(MockitoExtension::class)
class TradeSellProcessorTest {

    @InjectMocks
    private lateinit var tradeSellProcessor: TradeSellProcessor

    @Mock private lateinit var stockPriceStore: RealTimeStockPriceStore
    @Mock private lateinit var userAccountRepository: UserAccountRepository
    @Mock private lateinit var userStockRepository: UserStockRepository
    @Mock private lateinit var stockRepository: StockRepository
    @Mock private lateinit var tradeRepository: TradeRepository

    private lateinit var marketValidator: MockedStatic<MarketTimeValidator>

    @BeforeEach
    fun setUp() {
        marketValidator = mockStatic(MarketTimeValidator::class.java)
    }

    @AfterEach
    fun tearDown() {
        marketValidator.close()
    }

    // 공통 Mocking 설정을 위한 Helper 메서드 (코드 중복 제거)
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

        val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
        given(stockPriceStore.get(stock.stockCode)).willReturn(
            RealtimeStockPrice.builder().price("55000").tradeTime(nowTime).build()
        )
        given(userStockRepository.updateQuantity(anyLong(), anyLong(), anyLong())).willReturn(1)
        given(userAccountRepository.updateDepositAndPurchase(anyLong(), anyLong(), anyLong())).willReturn(1)
        given(userAccountRepository.findByUsersId(anyLong())).willReturn(Optional.of(account))

        // when
        val res = tradeSellProcessor.processSell(1L, TradeSellReq(1L, 10L, 10L, 50000L))

        // then
        assertThat(res.quantity).isEqualTo(10L)
        verify(tradeRepository).save(any())
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

        val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
        given(stockPriceStore.get(stock.stockCode)).willReturn(
            RealtimeStockPrice.builder().price("55000").tradeTime(nowTime).build()
        )
        given(userStockRepository.updateQuantity(anyLong(), anyLong(), anyLong())).willReturn(1)
        given(userAccountRepository.updateDepositAndPurchase(anyLong(), anyLong(), anyLong())).willReturn(1)
        given(userAccountRepository.findByUsersId(anyLong())).willReturn(Optional.of(account))

        // when
        tradeSellProcessor.processSell(1L, TradeSellReq(1L, 10L, 20L, 50000L))

        // then
        verify(userStockRepository).deleteByUserAndStock(eq(1L), eq(10L))
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

        val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
        given(stockPriceStore.get(stock.stockCode)).willReturn(
            RealtimeStockPrice.builder().price("55000").tradeTime(nowTime).build()
        )

        // when & then
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

        val nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
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