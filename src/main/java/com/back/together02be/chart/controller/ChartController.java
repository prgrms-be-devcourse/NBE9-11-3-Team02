package com.back.together02be.chart.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.back.together02be.chart.dto.response.ChartRes;
import com.back.together02be.chart.service.ChartService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stocks/{stockCode}/chart")
@RequiredArgsConstructor
public class ChartController {
	private final ChartService chartService;

	@GetMapping
	@Operation(summary = "특정 종목 차트 조회")
	public ResponseEntity<ChartRes> getChart(
		@PathVariable String stockCode,
		@RequestParam(defaultValue = "3M") String period
	) {
		return ResponseEntity.ok(chartService.getChart(stockCode, period));
	}
}
