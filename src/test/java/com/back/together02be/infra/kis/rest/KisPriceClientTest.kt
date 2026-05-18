package com.back.together02be.infra.kis.rest

import com.back.together02be.infra.kis.rest.service.KisTokenService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

@ExtendWith(MockitoExtension::class)
class KisPriceClientTest {

    private lateinit var kisPriceClient: KisPriceClient

    @Mock
    private lateinit var kisTokenService: KisTokenService

    private lateinit var mockServer: MockRestServiceServer

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        mockServer = MockRestServiceServer.bindTo(builder).build()
        val mockRestClient = builder.build()

        kisPriceClient = KisPriceClient(
            mockRestClient,
            kisTokenService,
            "test-app-key",
            "test-app-secret",
            "https://api.kis.com"
        )
    }

    @Test
    @DisplayName("정상적인 토큰과 종목코드로 현재가 API를 호출하면 데이터를 파싱하여 반환한다")
    fun getCurrentPrice_success() {
        val token = "mock-token"
        val stockCode = "005930" // 삼성전자

        val expectedUrl = "https://api.kis.com/uapi/domestic-stock/v1/quotations/inquire-price?fid_cond_mrkt_div_code=J&fid_input_iscd=$stockCode"

        val mockJsonResponse = """
            {
              "rt_cd": "0",
              "msg_cd": "MCA00000",
              "msg1": "정상처리",
              "output": {
                "stck_prpr": "75000",
                "prdy_vrss": "500",
                "prdy_vrss_sign": "2",
                "prdy_ctrt": "0.67"
              }
            }
        """.trimIndent()

        mockServer.expect(requestTo(expectedUrl))
            .andRespond(withSuccess(mockJsonResponse, MediaType.APPLICATION_JSON))


        val response = kisPriceClient.getCurrentPrice(token, stockCode)

        assertThat(response).isNotNull
        assertThat(response.returnCode).isEqualTo("0")
        assertThat(response.output.currentPrice).isEqualTo("75000")
    }
}