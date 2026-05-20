package com.back.together02be.stock.controller

import com.back.together02be.infra.kis.rest.KisPriceClient
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.service.RealTimeStockPriceStore
import com.back.together02be.support.ControllerTestSupport
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@DisplayName("StockController - SSE 엔드포인트 통합 테스트")
internal class StockControllerSseIntegrationTest : ControllerTestSupport() {
    // Mock
    @MockitoBean
    private lateinit var kisPriceClient: KisPriceClient

    @Autowired
    private lateinit var rtStockPriceStore: RealTimeStockPriceStore

    companion object {
        private const val SSE_URI = "/api/stocks/{stockCode}/sse"
        private const val VALID_CODE = "005930"
        private const val INVALID_CODE = "INVALID_99999"
        private const val ALL_SSE_URI = "/api/stocks/sse"
    }

    @BeforeEach
    fun setUp() {
        val price = RealtimeStockPrice(
            stockCode = VALID_CODE,
            price = "70000",
            changeSign = "2",
            change = "500",
            changeRate = "0.72",
            tradeTime = "143000"
        )
        rtStockPriceStore.put(VALID_CODE, price)
    }

    @Test
    @DisplayName("유효한 종목코드로 SSE 연결 시 text/event-stream 으로 응답한다")
    fun 정상_종목_SSE_스트림_수신() {
        mockMvc.perform(
            get(SSE_URI, VALID_CODE)
                .accept(MediaType.TEXT_EVENT_STREAM)
        )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.CONTENT_TYPE,
                    containsString("text/event-stream")
                )
            )
    }


    @Test
    @DisplayName("존재하지 않는 종목코드로 SSE 요청 시 404 를 반환한다")
    @Throws(Exception::class)
    fun 없는_종목코드_404() {
        mockMvc.perform(get(SSE_URI, INVALID_CODE))
            .andExpect(status().isNotFound())
    }

    @Test
    @DisplayName("전체 종목 SSE 연결 시 text/event-stream 으로 응답한다")
    fun 전체_종목_SSE_스트림_수신() {
        mockMvc.perform(get(ALL_SSE_URI)
            .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andExpect(header().string(
                HttpHeaders.CONTENT_TYPE,
                containsString("text/event-stream")))
    }

}
