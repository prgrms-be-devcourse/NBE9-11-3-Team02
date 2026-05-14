package com.back.together02be.ranking.dto.response;

import java.math.BigDecimal;

public record RankingRes(
        Long userId,
        String nickname,
        Integer rank,
        BigDecimal profitRate,
        Long totalAsset
) {
}