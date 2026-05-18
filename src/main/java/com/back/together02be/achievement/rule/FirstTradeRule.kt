package com.back.together02be.achievement.rule

import com.back.together02be.achievement.event.TradeCompletedEvent
import org.springframework.stereotype.Component

@Component
class FirstTradeRule : AchievementRule {
    override val targetAchievementCode = "FIRST_TRADE"

    override fun isSatisfied(event: TradeCompletedEvent) = event.tradeAmount > 0
}