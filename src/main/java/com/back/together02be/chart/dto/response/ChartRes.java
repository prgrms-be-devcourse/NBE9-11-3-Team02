package com.back.together02be.chart.dto.response;

import java.util.List;

import com.back.together02be.chart.constant.ChartPeriod;
import com.back.together02be.chart.dto.Candle;

public record ChartRes(
	String stockCode,
	String name,
	String period,
	String interval,
	List<CandleRes> candles
) {
	public static ChartRes of(String stockCode, String name, ChartPeriod period, List<Candle> candles) {
		List<CandleRes> dtos = candles.stream().map(CandleRes::from).toList();
		return new ChartRes(stockCode, name, period.getValue(),
			period == ChartPeriod.THREE_MONTHS ? "DAY" : "WEEK", dtos);
	}
}
