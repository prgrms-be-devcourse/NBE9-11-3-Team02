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

// Kotlin 전환 포인트:
// @Value로 주입받던 필드들을 주 생성자(Primary Constructor)로 끌어올려 코드를 훨씬 간결하게 만들고,
// Nullable(?) 기호를 제거하여 환경변수가 반드시 존재함을 보장하는 안전한 코드로 변경한다.
@Component
class KisPriceClient(
    private val kisTokenService: KisTokenService,
    @Value("\${kis.app-key}") private val appKey: String,
    @Value("\${kis.app-secret}") private val appSecret: String,
    @Value("\${kis.rest-base-url}") private val restBaseUrl: String
) {
    private val restClient: RestClient = RestClient.create()
    val accessToken: String

        get() = kisTokenService.getAccessToken()

    fun getCurrentPrice(token: String, stockCode: String): KisPriceRes {
        // Kotlin 전환 포인트:
        // 지저분한 문자열 덧셈(+) 연산을 코틀린의 문자열 템플릿($)을 사용하여 직관적이고 깔끔하게 연결한다.
        val url = "$restBaseUrl/uapi/domestic-stock/v1/quotations/inquire-price?fid_cond_mrkt_div_code=J&fid_input_iscd=$stockCode"

        val response = restClient.get()
            .uri(url)
            // Kotlin 전환 포인트:
            // 자바의 무거운 Consumer { headers -> ... } 함수형 인터페이스를 코틀린의 후행 람다(Trailing Lambda)로 가볍게 표현한다.
            .headers { headers ->
                headers.setBearerAuth(token)
                headers.set("appkey", appKey)
                headers.set("appsecret", appSecret)
                headers.set("tr_id", "FHKST01010100")
            }
            .retrieve()
            .body(KisPriceRes::class.java)

        // 엘비스 연산자(?.)를 사용하여 response가 null이 아닐 때만 output에 접근하도록 하여 NPE를 방지한다.
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
            // Kotlin 전환 포인트:
            // 자바의 Predicate, ErrorHandler 객체 생성을 생략하고 코틀린 람다로 우아하게 에러를 처리한다.
            .onStatus({ it.isError }) { _, res ->
                throw IllegalStateException("KIS 차트 API 호출 실패: HTTP ${res.statusCode}")
            }
            .body(KisChartApiRes::class.java)

        // Kotlin 전환 포인트:
        // isNullOrEmpty() 확장 함수를 사용하여 컬렉션의 Null 체크와 비어있는지(isEmpty) 여부를 단 한 번에 검증한다.
        check(!response?.output2.isNullOrEmpty()) { "KIS 차트 응답이 비어있습니다: code=$stockCode" }

        return response
    }
}