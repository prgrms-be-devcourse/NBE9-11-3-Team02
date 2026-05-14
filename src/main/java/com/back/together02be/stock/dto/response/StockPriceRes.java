package com.back.together02be.stock.dto.response;

import com.back.together02be.stock.entity.Stock;

public record StockPriceRes(
	Long stockId,
	String stockCode,
	String stockName
) {
	public static StockPriceRes from(Stock stock) {
		return new StockPriceRes(
			stock.getId(),
			stock.getStockCode(),
			stock.getStockName()
		);
	}
}
