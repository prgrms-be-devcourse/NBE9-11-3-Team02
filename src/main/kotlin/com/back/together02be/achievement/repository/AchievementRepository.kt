package com.back.together02be.achievement.repository

import com.back.together02be.achievement.entity.Achievement
import org.springframework.data.jpa.repository.JpaRepository

interface AchievementRepository : JpaRepository<Achievement, Long> {
    fun findByCode(targetCode: String): Achievement?
}