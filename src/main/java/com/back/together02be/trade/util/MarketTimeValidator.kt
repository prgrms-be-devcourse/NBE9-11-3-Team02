package com.back.together02be.trade.util

import java.time.*

object MarketTimeValidator {
    var clock: Clock = Clock.system(ZoneId.of("Asia/Seoul"))
    fun validateMarketOpen() {
        val now = LocalDateTime.now(clock)
        val day = now.dayOfWeek
        val time = now.toLocalTime()

        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw IllegalStateException("주말에는 거래할 수 없습니다.")
        }
        if (time.isBefore(LocalTime.of(9, 0)) || time.isAfter(LocalTime.of(15, 30))) {
            throw IllegalStateException("장 운영 시간(09:00 ~ 15:30) 외에는 거래가 불가능합니다.")
        }
    }
}