package com.back.together02be.chart.dto.response

import com.back.together02be.chart.constant.ChartPeriod
import com.back.together02be.chart.dto.Candle


data class ChartRes(
    val stockCode: String,
    val name: String,
    val period: String,
    val interval: String,
    val candles: List<CandleRes>
) {

    companion object {

        fun of(
            stockCode: String,
            name: String,
            period: ChartPeriod,
            candles: List<Candle>,
        ): ChartRes {
            val dtos = candles.map(CandleRes::from)
            return ChartRes(
                stockCode = stockCode,
                name = name,
                period = period.value,
                interval = if (period == ChartPeriod.THREE_MONTHS) "DAY" else "WEEK",
                candles = dtos,
            )
        }
    }
}
