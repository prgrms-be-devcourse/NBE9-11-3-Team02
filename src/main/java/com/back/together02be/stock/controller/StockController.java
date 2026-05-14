package com.back.together02be.stock.controller;

import com.back.together02be.global.apiRes.ApiRes;
import com.back.together02be.stock.dto.response.StockListRes;
import com.back.together02be.stock.dto.response.StockPriceRes;
import com.back.together02be.stock.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
@Tag(name = "StockController", description = "주식 API")
public class StockController {

    private final StockService stockService;

    @GetMapping
    public List<StockListRes> getStocks() {
        return stockService.getStocks();
    }

	@GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "전체 종목 실시간 정보 조회")
	public SseEmitter streamStocks() {
		return stockService.createStockListSseEmitter();
	}

	@GetMapping(value = "/{stockCode}/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Operation(summary = "특정 종목 실시간 정보 조회")
	public SseEmitter streamStockPrice(@PathVariable String stockCode) {
		return stockService.createSseEmitter(stockCode);
	}

	@GetMapping(value = "/{stockCode}")
	@Operation(summary = "특정 종목 정보 조회")
	public ResponseEntity<ApiRes<StockPriceRes>> getStockPrice(@PathVariable String stockCode) {
		return ResponseEntity.ok(new ApiRes<>("주식 정보 조회 완료", stockService.getStockPrice(stockCode)));
	}
}
