package com.back.together02be.asset.dto.response;

import com.back.together02be.asset.entity.UserStock;

public record UserStockRes(
        String stockCode,
        String stockName,
        Long quantity,
        Long averagePrice,
        Long currentPrice
) {
    public static UserStockRes from(UserStock userStock,Long currentPrice) {
        return new UserStockRes(
                userStock.getStock().getStockCode(),
                userStock.getStock().getStockName(),
                userStock.getQuantity(),
                userStock.getAveragePrice(),
                currentPrice

        );
    }
}