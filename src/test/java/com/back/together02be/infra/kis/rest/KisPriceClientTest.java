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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class KisPriceClientTest {

    @InjectMocks
    private KisPriceClient kisPriceClient;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient mockRestClient = builder.build();

        ReflectionTestUtils.setField(kisPriceClient, "restClient", mockRestClient);
        ReflectionTestUtils.setField(kisPriceClient, "appKey", "test-app-key");
        ReflectionTestUtils.setField(kisPriceClient, "appSecret", "test-app-secret");
        ReflectionTestUtils.setField(kisPriceClient, "restBaseUrl", "https://api.kis.com");
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

        // when
        KisPriceRes response = kisPriceClient.getCurrentPrice(token, stockCode);

        // then
        assertThat(response).isNotNull();
        assertThat(response.returnCode()).isEqualTo("0");
        assertThat(response.output().currentPrice()).isEqualTo("75000");
        assertThat(response.output().changeRate()).isEqualTo("0.67");

        mockServer.verify();
    }
}