package com.back.together02be.achievement.repository

import com.back.together02be.achievement.entity.UserAchievement
import org.springframework.data.jpa.repository.JpaRepository

interface UserAchievementRepository : JpaRepository<UserAchievement, Long> {
    fun existsByUsersIdAndAchievement_Code(usersId: Long, targetCode: String): Boolean
    fun findByUsersId(usersId: Long): List<UserAchievement>
}