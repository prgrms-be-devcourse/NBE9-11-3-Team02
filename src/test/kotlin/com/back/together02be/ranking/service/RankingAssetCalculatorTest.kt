package com.back.together02be.ranking.service

import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.entity.UserStock
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
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
@DisplayName("RankingAssetCalculator - 유저 자산 계산 로직 테스트")
internal class RankingAssetCalculatorTest {

    @Mock
    private lateinit var userStockRepository: UserStockRepository

    @Mock
    private lateinit var realTimeStockPriceStore: RealTimeStockPriceStore

    @InjectMocks
    private lateinit var rankingAssetCalculator: RankingAssetCalculator

    @Test
    @DisplayName("실시간 현재가가 정상 문자열이면 파싱된 현재가를 반환한다")
    fun 정상_현재가_파싱() {
        val price = RealtimeStockPrice(
            stockCode = "005930",
            price = "70000",
            changeSign = "",
            change = "",
            changeRate = "",
            tradeTime = null
        )

        val result = rankingAssetCalculator.extractCurrentPrice(price, 60000L)
        assertThat(result).isEqualTo(70000L)
    }

    @Test
    @DisplayName("실시간 현재가가 비정상(문자열 등)이면 평균 매입가를 반환한다")
    fun 비정상_현재가_대체() {
        val price = RealtimeStockPrice(
            stockCode = "005930",
            price = "이상한값",
            changeSign = "",
            change = "",
            changeRate = "",
            tradeTime = null
        )

        val result = rankingAssetCalculator.extractCurrentPrice(price, 60000L)
        assertThat(result).isEqualTo(60000L)
    }

    @Test
    @DisplayName("예수금과 보유 종목(실시간가 적용)의 총합을 정확히 계산한다")
    fun 총자산_계산_성공() {
        // given
        // Kotlin 전환 포인트: apply 스코프 함수를 사용해 객체 생성과 ID 세팅을 우아하게 묶습니다.
        val user = Users("user1", "password", "투자왕").apply {
            ReflectionTestUtils.setField(this, "id", 1L)
        }
        val account = UserAccount(user, 0L, 1000000L) // 예수금 100만

        val stock = Stock("005930", "삼성전자", StockMarket.KOSPI)
        val userStock = UserStock(user, stock, 10L, 60000L)

        given(userStockRepository.findAllByUsersId(1L))
            .willReturn(listOf(userStock))

        whenever(realTimeStockPriceStore.get("005930")).thenReturn(
            RealtimeStockPrice(
                stockCode = "",
                price = "70000", // 실시간가 7만 (총 70만)
                changeSign = "",
                change = "",
                changeRate = "",
                tradeTime = null
            )
        )

        // when
        val totalAsset = rankingAssetCalculator.calculateTotalAsset(account)

        // then
        assertThat(totalAsset).isEqualTo(1700000L) // 100만 + 70만
    }
}