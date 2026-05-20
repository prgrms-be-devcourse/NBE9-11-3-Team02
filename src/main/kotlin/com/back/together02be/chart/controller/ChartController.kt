package com.back.together02be.chart.controller

import com.back.together02be.chart.dto.response.ChartRes
import com.back.together02be.chart.service.ChartService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/stocks/{stockCode}/chart")
class ChartController(
    private val chartService: ChartService
) {
    @GetMapping
    @Operation(summary = "특정 종목 차트 조회")
    fun getChart(
        @PathVariable stockCode: String,
        @RequestParam(defaultValue = "3M") period: String
    ): ResponseEntity<ChartRes> =
        ResponseEntity.ok(chartService.getChart(stockCode, period))
}
