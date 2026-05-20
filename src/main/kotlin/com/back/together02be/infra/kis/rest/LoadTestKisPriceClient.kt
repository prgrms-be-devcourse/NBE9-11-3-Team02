package com.back.together02be.infra.kis.rest

import com.back.together02be.chart.constant.ChartPeriod
import com.back.together02be.chart.dto.response.KisChartApiRes
import com.back.together02be.infra.kis.rest.service.KisTokenService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * KIS OpenAPI 호출을 메모리 내 결정론적 데이터로 대체하는 Stub 구현체.
 *
 * loadtest 프로파일에서만 활성화되며, @Primary로 ChartService에 주입됨
 * 동일 stockCode → 항상 동일한 캔들 데이터 반환 (재현 가능)
 * Thread.sleep으로 실제 외부 API 지연 모사
 * 운영 코드(KisPriceClient) 변경 없음
 */
@Component
@Primary
@Profile("loadtest")
class LoadTestKisPriceClient(
    restClient: RestClient,
    kisTokenService: KisTokenService,
    @Value("\${kis.app-key}") appKey: String,
    @Value("\${kis.app-secret}") appSecret: String,
    @Value("\${kis.rest-base-url}") restBaseUrl: String,

    @Value("\${app.loadtest.kis.latency-ms:200}")
    private val latencyMs: Long,

    @Value("\${app.loadtest.kis.latency-jitter-ms:50}")
    private val jitterMs: Long,
) : KisPriceClient(restClient, kisTokenService, appKey, appSecret, restBaseUrl) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 실제 KIS 네트워크 호출 없이 결정론적 캔들 데이터 반환.
     * stockCode.hashCode()를 시드로 사용 → 같은 종목 = 같은 데이터.
     */
    override fun fetchCandles(stockCode: String, period: ChartPeriod): KisChartApiRes {
        simulateNetworkLatency(stockCode)

        val rng = Random(stockCode.hashCode().toLong())
        val candleCount = candleCountFor(period)
        val output2 = generateCandles(rng, candleCount)

        log.debug("[STUB] fetchCandles - stockCode={}, period={}, candles={}", stockCode, period, candleCount)

        return KisChartApiRes(
            rtCd = "0",
            msg1 = null,
            output1 = KisChartApiRes.Output1(htsKorIsnm = "테스트종목-$stockCode"),
            output2 = output2,
        )
    }

    // 내부 유틸

    // latencyMs ± jitterMs 범위의 균등 분포 지연
    private fun simulateNetworkLatency(stockCode: String) {
        val jitter = if (jitterMs > 0) (Math.random() * jitterMs * 2 - jitterMs).toLong() else 0L
        val sleep = max(0L, latencyMs + jitter)
        log.trace("[STUB] 네트워크 지연 모사 {}ms - stockCode={}", sleep, stockCode)
        Thread.sleep(sleep)
    }

    // period별 영업일 수 (주말 제외 단순 근사)
    private fun candleCountFor(period: ChartPeriod): Int = when (period) {
        ChartPeriod.THREE_MONTHS -> 66
        ChartPeriod.ONE_YEAR -> 252
    }

    /**
     * OHLCV 캔들 리스트 생성.
     *
     * 시작가: 10,000 ~ 100,000 사이 랜덤
     * 일 변동: ±3% 이내 랜덤 워크
     * high >= max(open, close), low <= min(open, close) 보장
     * 거래량: 100,000 ~ 10,000,000
     * 날짜: 오늘 기준 역순 영업일 (최신 → 과거 순 생성 후 그대로 반환;
     *         ChartService에서 sortedBy로 정렬하므로 순서 무관)
     */
    private fun generateCandles(rng: Random, count: Int): List<KisChartApiRes.Output2> {
        val fmt = DateTimeFormatter.BASIC_ISO_DATE   // yyyyMMdd

        var currentClose = 10_000L + (rng.nextDouble() * 90_000).toLong()
        var date = LocalDate.now()

        return (0 until count).map {
            // 다음 영업일로 이동 (주말 건너뜀)
            date = prevBusinessDay(date)

            val open = currentClose
            val close = (open * (1 + (rng.nextDouble() * 0.06 - 0.03))).roundToLong()
                .coerceAtLeast(1_000L)
            val high = max(open, close) + (rng.nextDouble() * open * 0.01).roundToLong()
            val low = min(open, close) - (rng.nextDouble() * open * 0.01).roundToLong()
                .coerceAtLeast(1_000L)
            val volume = 100_000L + (rng.nextDouble() * 9_900_000).roundToLong()

            currentClose = close

            KisChartApiRes.Output2(
                stckBsopDate = date.format(fmt),
                stckOprc = open.toString(),
                stckHgpr = high.toString(),
                stckLwpr = low.toString(),
                stckClpr = close.toString(),
                acmlVol = volume.toString(),
            )
        }
    }

    // 이전 영업일 반환 (토·일 건너뜀)
    private fun prevBusinessDay(date: LocalDate): LocalDate {
        var d = date.minusDays(1)
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) {
            d = d.minusDays(1)
        }
        return d
    }
}