package com.back.together02be.stock.entity;

import com.back.together02be.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Stock extends BaseEntity {

	@Column(nullable = false, unique = true)
	private String stockCode;

	@Column(nullable = false)
	private String stockName;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private StockMarket market;

	public Stock(String stockCode, String stockName, StockMarket market) {
		this.stockCode = stockCode;
		this.stockName = stockName;
		this.market = market;
	}

}