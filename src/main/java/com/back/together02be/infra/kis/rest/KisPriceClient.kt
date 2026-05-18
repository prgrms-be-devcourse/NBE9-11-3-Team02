package com.back.together02be.infra.kis.rest

import com.back.together02be.chart.constant.ChartPeriod
import com.back.together02be.chart.dto.response.KisChartApiRes
import com.back.together02be.infra.kis.rest.dto.KisPriceRes
import com.back.together02be.infra.kis.rest.service.KisTokenService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class KisPriceClient(
    private val restClient: RestClient,
    private val kisTokenService: KisTokenService,
    @Value("\${kis.app-key}") private val appKey: String,
    @Value("\${kis.app-secret}") private val appSecret: String,
    @Value("\${kis.rest-base-url}") private val restBaseUrl: String
) {
    val accessToken: String

        get() = kisTokenService.getAccessToken()

    fun getCurrentPrice(token: String, stockCode: String): KisPriceRes {
        val url = "$restBaseUrl/uapi/domestic-stock/v1/quotations/inquire-price?fid_cond_mrkt_div_code=J&fid_input_iscd=$stockCode"

        val response = restClient.get()
            .uri(url)
            .headers { headers ->
                headers.setBearerAuth(token)
                headers.set("appkey", appKey)
                headers.set("appsecret", appSecret)
                headers.set("tr_id", "FHKST01010100")
            }
            .retrieve()
            .body(KisPriceRes::class.java)

        // 엘비스 연산자(?.)를 사용하여 response가 null이 아닐 때만 output에 접근하도록 하여 NPE를 방지
        check(response?.output != null) { "현재가 조회 실패: stockCode=$stockCode" }

        return response
    }

    fun fetchCandles(stockCode: String, period: ChartPeriod): KisChartApiRes {
        val token = this.accessToken
        val endDate = LocalDate.now()
        val startDate = period.startDate(endDate)

        val url = "$restBaseUrl/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice" +
            "?FID_COND_MRKT_DIV_CODE=J" +
            "&FID_INPUT_ISCD=$stockCode" +
            "&FID_INPUT_DATE_1=${startDate.format(DateTimeFormatter.BASIC_ISO_DATE)}" +
            "&FID_INPUT_DATE_2=${endDate.format(DateTimeFormatter.BASIC_ISO_DATE)}" +
            "&FID_PERIOD_DIV_CODE=${period.kisPeriodCode}" +
            "&FID_ORG_ADJ_PRC=0"

        val response = restClient.get()
            .uri(url)
            .headers { headers ->
                headers.setBearerAuth(token)
                headers.set("appkey", appKey)
                headers.set("appsecret", appSecret)
                headers.set("tr_id", "FHKST03010100")
                headers.set("custtype", "P")
            }
            .retrieve()
            .onStatus({ it.isError }) { _, res ->
                throw IllegalStateException("KIS 차트 API 호출 실패: HTTP ${res.statusCode}")
            }
            .body(KisChartApiRes::class.java)

        check(!response?.output2.isNullOrEmpty()) { "KIS 차트 응답이 비어있습니다: code=$stockCode" }

        return response
    }
}
