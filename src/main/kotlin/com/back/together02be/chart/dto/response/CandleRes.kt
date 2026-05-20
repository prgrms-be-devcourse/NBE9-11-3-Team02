package com.back.together02be.chart.dto.response

import com.back.together02be.chart.dto.Candle
import java.math.BigDecimal

data class CandleRes(
    val time: String,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long
) {
    companion object {
        fun from(candle: Candle): CandleRes =
            CandleRes(
                time = candle.date.toString(),  // yyyy-MM-dd
                open = candle.open,
                high = candle.high,
                low = candle.low,
                close = candle.close,
                volume = candle.volume
            )
    }
}
