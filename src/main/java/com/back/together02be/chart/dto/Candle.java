package com.back.together02be.chart.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Candle(
	LocalDate date,
	BigDecimal open,
	BigDecimal high,
	BigDecimal low,
	BigDecimal close,
	Long volume
) {
}
