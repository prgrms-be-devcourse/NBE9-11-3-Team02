package com.back.together02be.asset.dto.response

import com.back.together02be.asset.entity.UserStock

data class UserStockRes(
    val stockCode: String,
    val stockName: String,
    val quantity: Long,
    val averagePrice: Long,
    val currentPrice: Long
) {
    companion object {
        @JvmStatic
        fun from(userStock: UserStock, currentPrice: Long) = UserStockRes(
            stockCode = userStock.stock.stockCode,
            stockName = userStock.stock.stockName,
            quantity = userStock.quantity,
            averagePrice = userStock.averagePrice,
            currentPrice = currentPrice
        )
    }
}