package com.back.together02be.trade.entity;

import java.time.LocalDateTime;

import com.back.together02be.global.entity.BaseEntity;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.users.entity.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Trade extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "users_id", nullable = false)
	private Users users;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "stock_id", nullable = false)
	private Stock stock;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TradeType type;

	@Column(nullable = false)
	private Long quantity;

	@Column(nullable = false)
	private Long price;

	@Column(nullable = false)
	private Long amount;

	private Long profit;

	@Column(name = "traded_at", nullable = false)
	private LocalDateTime tradedAt;

	public Trade(Users users, Stock stock, TradeType type, Long quantity, Long price, Long profit) {
		this.users = users;
		this.stock = stock;
		this.type = type;
		this.quantity = quantity;
		this.price = price;
		this.amount = (long) quantity * price;
		this.profit = profit;
		this.tradedAt = LocalDateTime.now();
	}

	// 매수: profit은 무조건 null
	public static Trade buy(Users users, Stock stock,
		Long quantity, Long price) {
		return new Trade(users, stock, TradeType.BUY, quantity, price, null);
	}

	// 매도: profit 필수
	public static Trade sell(Users users, Stock stock,
		Long quantity, Long price, Long profit) {
		return new Trade(users, stock, TradeType.SELL, quantity, price, profit);
	}
}
