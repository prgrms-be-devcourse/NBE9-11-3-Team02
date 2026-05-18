package com.back.together02be.asset.dto.response

data class TotalPurchaseRes(
    val nickname: String,
    val deposit: Long,
    val totalAmount: Long,
    val stocks: List<StockInfoRes>
)