package com.back.together02be.achievement.dto

import java.time.LocalDateTime

data class AchievementRes(
    val code: String,
    val name: String,
    val description: String,
    val isAchieved: Boolean,
    val achievedAt: LocalDateTime?
)