package com.back.together02be.trade.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.together02be.trade.entity.Trade;

public interface TradeRepository extends JpaRepository<Trade, Long> {
}
