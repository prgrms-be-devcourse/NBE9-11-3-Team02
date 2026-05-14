package com.back.together02be.achievement.rule;

import com.back.together02be.achievement.event.TradeCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BigSpenderRule implements AchievementRule {
    @Override
    public String getTargetAchievementCode() {
        return "BIG_SPENDER";
    }

    @Override
    public boolean isSatisfied(TradeCompletedEvent event) {
        return event.totalPurchaseAmount() >= 10_000_000L;
    }
}
