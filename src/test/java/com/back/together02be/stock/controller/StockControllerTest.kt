package com.back.together02be.stock.controller

import com.back.together02be.stock.dto.response.StockListRes
import com.back.together02be.stock.dto.response.StockPriceRes
import com.back.together02be.stock.service.StockService
import com.back.together02be.support.ControllerTestSupport
import jakarta.persistence.EntityNotFoundException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
@DisplayName("StockController 통합 테스트")
internal class StockControllerTest : ControllerTestSupport() {
    @MockitoBean
    private lateinit var stockService: StockService

    companion object {
        private const val STOCK_URI = "/api/stocks/{stockCode}"
        private const val VALID_CODE = "005930"
        private const val ALL_STOCKS_URI = "/api/stocks"
    }

    @Test
    @DisplayName("유효한 종목코드로 요청하면 200 OK와 ApiRes 구조로 응답한다")
    fun getStockPrice_success() {
        // given
        val response = StockPriceRes(1L, VALID_CODE, "삼성전자")
        whenever(stockService.getStockPrice(VALID_CODE)).thenReturn(response)

        // when & then
        mockMvc.perform(get(STOCK_URI, VALID_CODE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("주식 정보 조회 완료"))
            .andExpect(jsonPath("$.data.stockId").value(1))
            .andExpect(jsonPath("$.data.stockCode").value(VALID_CODE))
            .andExpect(jsonPath("$.data.stockName").value("삼성전자"))
    }

    @Test
    @DisplayName("존재하지 않는 종목코드로 요청하면 404를 반환한다")
    fun getStockPrice_notFound() {
        // given
        whenever(stockService.getStockPrice("INVALID"))
            .thenThrow(EntityNotFoundException("존재하지 않는 종목코드입니다: INVALID"))

        // when & then
        mockMvc.perform(get(STOCK_URI, "INVALID"))
            .andExpect(status().isNotFound())
    }

    // 전체 종목 조회 REST 테스트 (ST-01)
    @Test
    @DisplayName("전체 종목을 요청하면 200 OK와 함께 종목 리스트를 반환한다")
    @Throws(Exception::class)
    fun getStocks_success() {
        // given
        val response = listOf(
            StockListRes(1L, "005930", "삼성전자", 70000L, 2.19),
            StockListRes(2L, "000660", "SK하이닉스", 180000L, -1.12)
        )
        whenever(stockService.getStocks()).thenReturn(response)

        // when & then
        mockMvc.perform(get(ALL_STOCKS_URI))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].stockCode").value("005930"))
            .andExpect(jsonPath("$[0].stockName").value("삼성전자"))
            .andExpect(jsonPath("$[0].currentPrice").value(70000))
            .andExpect(jsonPath("$[0].changeRate").value(2.19))
            .andExpect(jsonPath("$[1].stockCode").value("000660"))
    }

}
