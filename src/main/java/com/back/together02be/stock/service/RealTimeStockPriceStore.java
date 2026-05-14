package com.back.together02be.stock.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.back.together02be.stock.dto.RealtimeStockPrice;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RealTimeStockPriceStore {

	private final ConcurrentHashMap<String, RealtimeStockPrice> priceMap = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, AtomicInteger> subscriberCount = new ConcurrentHashMap<>();

	// 실시간 시세 넣기
	public void put(String stockCode, RealtimeStockPrice stockPrice) {
		priceMap.put(stockCode, stockPrice);
	}

	// REST 시딩
	public void putIfAbsent(String stockCode, RealtimeStockPrice price) {
		priceMap.putIfAbsent(stockCode, price);
	}

	// 실시간 시세 꺼내기
	public RealtimeStockPrice get(String stockCode) {
		return priceMap.get(stockCode);
	}
}
