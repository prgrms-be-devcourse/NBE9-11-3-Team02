package com.back.together02be.chart.constant

import java.time.LocalDate

enum class ChartPeriod(
    val value: String,
    val kisPeriodCode: String,
    val lookbackDays: Int
) {
    THREE_MONTHS("3M", "D", 90),
    ONE_YEAR("1Y", "W", 365);

    fun startDate(endDate: LocalDate): LocalDate =
        endDate.minusDays(lookbackDays.toLong())

    companion object {
        fun from(value: String): ChartPeriod =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("지원하지 않는 기간: $value")
    }
}
