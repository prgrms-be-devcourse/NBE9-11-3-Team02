package com.back.together02be.asset.service

import com.back.together02be.asset.entity.UserStock
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.entity.Stock
import com.back.together02be.stock.entity.StockMarket
import com.back.together02be.stock.service.RealTimeStockPriceStore
import com.back.together02be.users.entity.Users
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class AssetServiceTest {

    // 코틀린에서는 의존성 주입을 위해 지연 초기화(lateinit)를 사용합니다.
    @Mock
    lateinit var userStockRepository: UserStockRepository

    @Mock
    lateinit var realTimeStockPriceStore: RealTimeStockPriceStore

    @Mock
    lateinit var userAccountRepository: UserAccountRepository

    @Mock
    lateinit var userStockSseService: UserStockSseService

    @InjectMocks
    lateinit var assetService: AssetService

    @Test
    @DisplayName("보유 종목 목록 및 실시간 현재가 정상 매핑 테스트")
    fun getUserStocks_Success() {
        // given
        val userId = 1L
        val user = Users("testuser", "pw", "테스터")
        val stock1 = Stock("005930", "삼성전자", StockMarket.KOSPI)
        val userStock1 = UserStock(user, stock1, 10L, 50000L)

        val mockPrice = RealtimeStockPrice(
            stockCode = "005930",
            price = "75000",
            changeSign = "",
            change = "",
            changeRate = "",
            tradeTime = null
        )

        // 코틀린에서 when은 예약어이므로 백틱(`)으로 감싸서 호출해야 합니다.
        `when`(userStockRepository.findAllByUsersId(userId)).thenReturn(listOf(userStock1))
        `when`(realTimeStockPriceStore.get("005930")).thenReturn(mockPrice)

        // when
        val result = assetService.getUserStocks(userId)

        // then
        assertThat(result).hasSize(1)
        // List의 첫 번째 요소 접근 시 getFirst() 대신 인덱스 [0] 또는 first() 프로퍼티 사용
        assertThat(result[0].stockCode).isEqualTo("005930")
        assertThat(result[0].quantity).isEqualTo(10L)
        assertThat(result[0].currentPrice).isEqualTo(75000L)
    }

    @Test
    @DisplayName("실시간 현재가 캐시 누락 시 0원으로 반환 방어 로직 테스트")
    fun getUserStocks_WhenCacheMiss_ReturnsZero() {
        // given
        val userId = 1L
        val user = Users("testuser", "pw", "테스터")
        val stock1 = Stock("005930", "삼성전자", StockMarket.KOSPI)
        val userStock1 = UserStock(user, stock1, 10L, 50000L)

        `when`(userStockRepository.findAllByUsersId(userId)).thenReturn(listOf(userStock1))
        `when`(realTimeStockPriceStore.get(anyString())).thenReturn(null)

        // when
        val result = assetService.getUserStocks(userId)

        // then
        assertThat(result).hasSize(1)
        assertThat(result[0].currentPrice).isEqualTo(0L)
    }
}