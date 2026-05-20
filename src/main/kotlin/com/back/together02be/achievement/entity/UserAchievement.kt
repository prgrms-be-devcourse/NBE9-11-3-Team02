package com.back.together02be.achievement.entity

import com.back.together02be.global.entity.BaseEntity
import com.back.together02be.users.entity.Users
import jakarta.persistence.*

@Entity
@Table(uniqueConstraints = [
    UniqueConstraint(name = "uk_users_achievement", columnNames = ["users_id", "achievement_id"])
])
class UserAchievement(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    val users: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false)
    val achievement: Achievement
) : BaseEntity()