package com.back.together02be.trade.dto.response;

public record TradeSellRes(
        Long tradeId,
        String stockName,
        Long quantity,
        Long price,
        Long amount,
        Long remainingDeposit
) {
}
