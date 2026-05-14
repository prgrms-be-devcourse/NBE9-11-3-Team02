package com.back.together02be.trade.util;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public class MarketTimeValidator {

    public static void validateMarketOpen() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        DayOfWeek day = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        // 주말 체크
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw new IllegalStateException("주말에는 거래할 수 없습니다.");
        }

        // 시간 체크 (09:00 ~ 15:30)
        if (time.isBefore(LocalTime.of(9, 0)) || time.isAfter(LocalTime.of(15, 30))) {
            throw new IllegalStateException("장 운영 시간(09:00 ~ 15:30) 외에는 거래가 불가능합니다.");
        }
    }
}