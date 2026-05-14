package com.back.together02be.chart.constant;

import java.time.LocalDate;
import java.util.Arrays;

import lombok.Getter;

@Getter
public enum ChartPeriod {
	THREE_MONTHS("3M", "D", 90),
	ONE_YEAR("1Y", "W", 365);

	private final String value;
	private final String kisPeriodCode;
	private final int lookbackDays;

	ChartPeriod(String value, String kisPeriodCode, int lookbackDays) {
		this.value = value;
		this.kisPeriodCode = kisPeriodCode;
		this.lookbackDays = lookbackDays;
	}

	public static ChartPeriod from(String value) {
		return Arrays.stream(values())
			.filter(p -> p.value.equalsIgnoreCase(value))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("지원하지 않는 기간: " + value));
	}

	public LocalDate startDate(LocalDate endDate) {
		return endDate.minusDays(lookbackDays);
	}
}
