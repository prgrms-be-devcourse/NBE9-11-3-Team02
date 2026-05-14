package com.back.together02be.trade.dto.request;

import jakarta.validation.constraints.Min;

public record TradeSellReq(
        Long userId,
        Long stockId,
        @Min(value = 1, message = "매도 수량은 최소 1개여야 합니다.")
        Long quantity,
        Long expectedPrice
){ }