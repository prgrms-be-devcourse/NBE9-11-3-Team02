package com.back.together02be.stock.dto.response

import com.back.together02be.stock.entity.Stock

data class StockPriceRes(
	val stockId: Long,
	val stockCode: String,
	val stockName: String
) {
    companion object {
		fun from(stock: Stock): StockPriceRes {
            return StockPriceRes(
                stock.id,
                stock.stockCode,
                stock.stockName
            )
        }
    }
}
