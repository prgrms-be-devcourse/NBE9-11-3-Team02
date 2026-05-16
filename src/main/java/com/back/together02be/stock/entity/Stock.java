package com.back.together02be.stock.entity;

import com.back.together02be.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
public class Stock extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String stockCode;

    @Column(nullable = false)
    private String stockName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StockMarket market;

    protected Stock() {}

    public Stock(String stockCode, String stockName, StockMarket market) {
        this.stockCode = stockCode;
        this.stockName = stockName;
        this.market = market;
    }

    public String getStockCode() { return stockCode; }
    public String getStockName() { return stockName; }
    public StockMarket getMarket() { return market; }
}
