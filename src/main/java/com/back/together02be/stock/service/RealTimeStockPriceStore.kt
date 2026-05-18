package com.back.together02be.stock.service

import com.back.together02be.stock.dto.RealtimeStockPrice
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class RealTimeStockPriceStore {
    private val priceMap = ConcurrentHashMap<String, RealtimeStockPrice>()

    // 실시간 시세 넣기
    fun put(stockCode: String, stockPrice: RealtimeStockPrice) {
        priceMap[stockCode] = stockPrice
    }

    // REST 시딩
    fun putIfAbsent(stockCode: String, price: RealtimeStockPrice) {
        priceMap.putIfAbsent(stockCode, price)
    }

    // 실시간 시세 꺼내기
    fun get(stockCode: String): RealtimeStockPrice? =
        priceMap[stockCode]
}
