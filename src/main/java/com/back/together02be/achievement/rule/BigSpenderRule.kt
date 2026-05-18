package com.back.together02be.achievement.rule

import com.back.together02be.achievement.event.TradeCompletedEvent
import org.springframework.stereotype.Component

@Component
class BigSpenderRule : AchievementRule {
    override val targetAchievementCode = "BIG_SPENDER"

    // 단일 표현식 함수(Expression Body) 활용
    override fun isSatisfied(event: TradeCompletedEvent) = event.totalPurchaseAmount >= 10_000_000L
}