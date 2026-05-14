package com.back.together02be.infra.kis.rest;

import com.back.together02be.chart.constant.ChartPeriod;
import com.back.together02be.chart.dto.response.KisChartApiRes;
import com.back.together02be.infra.kis.rest.service.KisTokenService;
import com.back.together02be.infra.kis.rest.dto.KisPriceRes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class KisPriceClient {

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    @Value("${kis.rest-base-url}")
    private String restBaseUrl;

    private final RestClient restClient = RestClient.create();
    private final KisTokenService kisTokenService;

    public KisPriceClient(KisTokenService kisTokenService) {
        this.kisTokenService = kisTokenService;
    }

    // 현재 사용 가능한 access token 반환
    public String getAccessToken() {
        return kisTokenService.getAccessToken();
    }

    // 토큰 + 종목코드 받아서 현재가 조회
    public KisPriceRes getCurrentPrice(String token, String stockCode) {
        String url = restBaseUrl + "/uapi/domestic-stock/v1/quotations/inquire-price"
                + "?fid_cond_mrkt_div_code=J"
                + "&fid_input_iscd=" + stockCode;

        KisPriceRes response = restClient.get()
                .uri(url)
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.set("appkey", appKey);
                    headers.set("appsecret", appSecret);
                    headers.set("tr_id", "FHKST01010100");
                })
                .retrieve()
                .body(KisPriceRes.class);

        if (response == null || response.output() == null) {
            throw new IllegalStateException("현재가 조회 실패: stockCode=" + stockCode);
        }

        return response;
    }

    public KisChartApiRes fetchCandles(String stockCode, ChartPeriod period) {
        String token = getAccessToken();

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = period.startDate(endDate);

        String url = restBaseUrl
                + "/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice"
                + "?FID_COND_MRKT_DIV_CODE=J"
                + "&FID_INPUT_ISCD=" + stockCode
                + "&FID_INPUT_DATE_1=" + startDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&FID_INPUT_DATE_2=" + endDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "&FID_PERIOD_DIV_CODE=" + period.getKisPeriodCode()
                + "&FID_ORG_ADJ_PRC=0";

        KisChartApiRes response = restClient.get()
                .uri(url)
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.set("appkey", appKey);
                    headers.set("appsecret", appSecret);
                    headers.set("tr_id", "FHKST03010100");
                    headers.set("custtype", "P");
                })
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new IllegalStateException("KIS 차트 API 호출 실패: HTTP " + res.getStatusCode());
                })
                .body(KisChartApiRes.class);

        if (response == null || response.output2() == null || response.output2().isEmpty()) {
            throw new IllegalStateException("KIS 차트 응답이 비어있습니다: code=" + stockCode);
        }

        return response;
    }
}