package com.back.together02be.chart.dto

import java.math.BigDecimal
import java.time.LocalDate

data class Candle(
	val date: LocalDate,
	val open: BigDecimal,
	val high: BigDecimal,
	val low: BigDecimal,
	val close: BigDecimal,
	val volume: Long
)
