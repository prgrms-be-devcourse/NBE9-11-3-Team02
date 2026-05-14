package com.back.together02be.chart.dto.response;

import java.math.BigDecimal;

import com.back.together02be.chart.dto.Candle;

public record CandleRes(
	String time,
	BigDecimal open,
	BigDecimal high,
	BigDecimal low,
	BigDecimal close,
	Long volume
) {
	public static CandleRes from(Candle candle) {
		return new CandleRes(
			candle.date().toString(), // yyyy-MM-dd
			candle.open(), candle.high(), candle.low(), candle.close(), candle.volume()
		);
	}
}
