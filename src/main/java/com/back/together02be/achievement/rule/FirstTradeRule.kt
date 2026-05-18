package com.back.together02be.achievement.rule;

import com.back.together02be.achievement.event.TradeCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FirstTradeRule implements AchievementRule {
    @Override
    public String getTargetAchievementCode() {
        return "FIRST_TRADE"; // DB에 저장된 code와 동일해야 함
    }

    @Override
    public boolean isSatisfied(TradeCompletedEvent event) {
        // 실제로는 DB에서 해당 업적을 이미 가졌는지 조회하는 로직이 들어갑니다.
        return event.tradeAmount() > 0;
    }
}