package com.back.together02be.trade.dto;

public record BuyRes(
        Long tradeId,
        String stockName,
        Long quantity,
        Long price,
        Long amount,
        Long remainingDeposit
) {
}
