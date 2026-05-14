package com.back.together02be.asset.dto.response;

import java.util.List;

public record TotalPurchaseRes(
        String nickname,
        long deposit,
        long totalAmount,
        List<StockInfoRes> stocks
) { }