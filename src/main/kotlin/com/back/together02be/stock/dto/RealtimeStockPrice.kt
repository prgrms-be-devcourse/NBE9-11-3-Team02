package com.back.together02be.stock.dto

import com.back.together02be.infra.kis.rest.dto.KisPriceRes

data class RealtimeStockPrice (
    val stockCode: String,      // 종목코드
    val price: String,          // 현재가
    val changeSign: String,     // 전일 대비 부호 (1:상한 2:상승 3:보합 4:하한 5:하락)
    val change: String,         // 전일 대비
    val changeRate: String,     // 전일 대비율
    val tradeTime: String?      // 체결시간 (REST 시딩 시 null)
) {
    companion object {
        // REST 시딩용 변환
        fun fromRest(stockCode: String, output: KisPriceRes.Output) = RealtimeStockPrice(
            stockCode = stockCode,
            price = output.currentPrice, // 현재가
            changeSign = output.changeSign, // 전일 대비 부호
            change = output.priceDifference, // 전일 대비 금액
            changeRate = output.changeRate, // 전일 대비율
            tradeTime = null // rest-> 체결 시간 없음
        )
    }
}
