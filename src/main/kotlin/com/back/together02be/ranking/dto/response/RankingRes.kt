package com.back.together02be.ranking.dto.response

import java.math.BigDecimal

data class RankingRes(
    val userId: Long,
    val nickname: String,
    val rank: Int,
    val profitRate: BigDecimal,
    val totalAsset: Long
)