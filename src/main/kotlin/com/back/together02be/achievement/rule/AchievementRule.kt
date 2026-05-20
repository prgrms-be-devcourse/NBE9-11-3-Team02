package com.back.together02be.achievement.rule

import com.back.together02be.achievement.event.TradeCompletedEvent

interface AchievementRule {
    val targetAchievementCode: String
    val defaultName: String get() = targetAchievementCode
    val defaultDescription: String get() = "자동 등록된 업적입니다."

    fun isSatisfied(event: TradeCompletedEvent): Boolean
}