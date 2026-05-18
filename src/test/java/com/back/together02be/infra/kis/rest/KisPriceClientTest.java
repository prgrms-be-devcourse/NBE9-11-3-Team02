package com.back.together02be.infra.kis.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.back.together02be.infra.kis.rest.dto.KisPriceRes;
import com.back.together02be.infra.kis.rest.service.KisTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class KisPriceClientTest {

    private KisPriceClient kisPriceClient;

    @Mock
    private KisTokenService kisTokenService;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient mockRestClient = builder.build();

        kisPriceClient = new KisPriceClient(
                mockRestClient,
                kisTokenService,
                "test-app-key",
                "test-app-secret",
                "https://api.kis.com"
        );
    }

    @Test
    @DisplayName("정상적인 토큰과 종목코드로 현재가 API를 호출하면 데이터를 파싱하여 반환한다")
    void getCurrentPrice_success() {
        String token = "mock-token";
        String stockCode = "005930"; // 삼성전자

        String expectedUrl = "https://api.kis.com/uapi/domestic-stock/v1/quotations/inquire-price?fid_cond_mrkt_div_code=J&fid_input_iscd=" + stockCode;
        String mockJsonResponse = """
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
                """;

        mockServer.expect(requestTo(expectedUrl))
                .andRespond(withSuccess(mockJsonResponse, MediaType.APPLICATION_JSON));

        KisPriceRes response = kisPriceClient.getCurrentPrice(token, stockCode);

        assertThat(response).isNotNull();
        assertThat(response.getReturnCode()).isEqualTo("0");
        assertThat(response.getOutput().getCurrentPrice()).isEqualTo("75000");
    }
}