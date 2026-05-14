package com.back.together02be.chart.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.back.together02be.chart.constant.ChartPeriod;
import com.back.together02be.chart.dto.Candle;
import com.back.together02be.chart.dto.response.ChartRes;
import com.back.together02be.chart.dto.response.KisChartApiRes;
import com.back.together02be.infra.kis.rest.KisPriceClient;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChartService {

	private final KisPriceClient kisPriceClient;

	@Cacheable(value = "chart", key = "#stockCode + ':' + #periodValue")
	public ChartRes getChart(String stockCode, String periodValue) {

		ChartPeriod period = ChartPeriod.from(periodValue);

		log.info("종목 차트 캐시 미스 → KIS API 호출 - stockCode={}, period={}", stockCode, periodValue);
		KisChartApiRes response = kisPriceClient.fetchCandles(stockCode, period);

		List<Candle> candles = response.output2().stream()
			.map(o -> new Candle(
				LocalDate.parse(o.stckBsopDate(), DateTimeFormatter.BASIC_ISO_DATE),
				new BigDecimal(o.stckOprc()),
				new BigDecimal(o.stckHgpr()),
				new BigDecimal(o.stckLwpr()),
				new BigDecimal(o.stckClpr()),
				Long.parseLong(o.acmlVol())
			))
			.sorted(Comparator.comparing(Candle::date))
			.toList();

		if (candles.isEmpty()) {
			throw new EntityNotFoundException("종목 데이터를 찾을 수 없습니다: " + stockCode);
		}

		String stockName = response.output1() != null ? response.output1().htsKorIsnm() : "";

		return ChartRes.of(stockCode, stockName, period, candles);
	}
}
