package com.back.together02be.achievement.entity;

import com.back.together02be.global.entity.BaseEntity;
import com.back.together02be.users.entity.Users;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_achievement", columnNames = {"users_id", "achievement_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAchievement extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    private Users users;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    public UserAchievement(Users users, Achievement achievement) {
        this.users = users;
        this.achievement = achievement;
    }
}