package com.back.together02be.chart.service

import com.back.together02be.chart.constant.ChartPeriod
import com.back.together02be.chart.dto.Candle
import com.back.together02be.chart.dto.response.ChartRes
import com.back.together02be.infra.kis.rest.KisPriceClient
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val log = LoggerFactory.getLogger(ChartService::class.java)

@Service
class ChartService(
    private val kisPriceClient: KisPriceClient
) {

    @Cacheable(value = ["chart"], key = "#stockCode + ':' + #periodValue")
    fun getChart(stockCode: String, periodValue: String): ChartRes {

        val period = ChartPeriod.from(periodValue)

        log.info("종목 차트 캐시 미스 → KIS API 호출 - stockCode={}, period={}", stockCode, periodValue)
        val response = kisPriceClient.fetchCandles(stockCode, period)

        val candles = response.output2
            .map { o ->
                Candle(
                    date = LocalDate.parse(o.stckBsopDate, DateTimeFormatter.BASIC_ISO_DATE),
                    open = BigDecimal(o.stckOprc),
                    high = BigDecimal(o.stckHgpr),
                    low = BigDecimal(o.stckLwpr),
                    close = BigDecimal(o.stckClpr),
                    volume = o.acmlVol.toLong(),
                )
            }
            .sortedBy { it.date }

        if (candles.isEmpty()) {
            throw EntityNotFoundException("종목 데이터를 찾을 수 없습니다: $stockCode")
        }

        val stockName = response.output1?.htsKorIsnm ?: ""

        return ChartRes.of(stockCode, stockName, period, candles)
    }
}
