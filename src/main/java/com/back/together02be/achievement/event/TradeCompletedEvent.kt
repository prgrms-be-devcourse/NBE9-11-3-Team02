package com.back.together02be.achievement.event;

public record TradeCompletedEvent(
        Long userId,
        Long tradeAmount,         // 이번 결제 금액
        Long totalPurchaseAmount  // 누적 총 매수 금액 (UserAccount 기준)
) {}