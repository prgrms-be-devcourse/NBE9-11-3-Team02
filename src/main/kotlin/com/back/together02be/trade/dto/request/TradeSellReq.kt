package com.back.together02be.trade.dto.request

import jakarta.validation.constraints.Min

data class TradeSellReq(
    val userId: Long?,
    val stockId: Long,

    @field:Min(value = 1, message = "매도 수량은 최소 1개여야 합니다.")
    val quantity: Long,

    val expectedPrice: Long?=null
)