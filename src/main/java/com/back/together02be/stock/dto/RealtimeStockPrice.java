package com.back.together02be.stock.dto;

import com.back.together02be.infra.kis.rest.dto.KisPriceRes;

import lombok.Builder;

@Builder
public class RealtimeStockPrice {
    private String stockCode;
    private String price;
    private String changeSign;
    private String change;
    private String changeRate;
    private String tradeTime;

    public String getStockCode() { return stockCode; }
    public String getPrice() { return price; }
    public String getChangeSign() { return changeSign; }
    public String getChange() { return change; }
    public String getChangeRate() { return changeRate; }
    public String getTradeTime() { return tradeTime; }

    public static RealtimeStockPrice fromRest(String stockCode, KisPriceRes.Output output) {
        return RealtimeStockPrice.builder()
            .stockCode(stockCode)
            .price(output.currentPrice())
            .changeSign(output.changeSign())
            .change(output.priceDifference())
            .changeRate(output.changeRate())
            .tradeTime(null)
            .build();
    }
}
