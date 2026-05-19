package com.back.together02be.trade.dto.response

data class TradeSellRes(
    val tradeId: Long,
    val stockName: String,
    val quantity: Long,
    val price: Long,
    val amount: Long,
    val remainingDeposit: Long
)
