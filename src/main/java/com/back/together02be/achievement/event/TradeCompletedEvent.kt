package com.back.together02be.achievement.event

data class TradeCompletedEvent(
    val userId: Long,
    val tradeAmount: Long,
    val totalPurchaseAmount: Long
)