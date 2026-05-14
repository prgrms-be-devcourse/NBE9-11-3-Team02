package com.back.together02be.achievement;

import com.back.together02be.achievement.event.TradeCompletedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

@SpringBootTest
public class AchievementTest {
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Test
    @DisplayName("전략 패턴과 EasyRules 동작 동시 확인 테스트")
    void testAchievements() {
        System.out.println("====== 이벤트 1: 첫 주식 매수 (금액 500,000원) ======");
        TradeCompletedEvent event1 = new TradeCompletedEvent(1L, 500_000L, 500_000L);
        eventPublisher.publishEvent(event1);

        System.out.println("\n====== 이벤트 2: 대규모 주식 매수 (누적 15,000,000원) ======");
        TradeCompletedEvent event2 = new TradeCompletedEvent(1L, 14_500_000L, 15_000_000L);
        eventPublisher.publishEvent(event2);
    }
}
