package com.back.together02be.achievement.dto;

import java.time.LocalDateTime;

public record AchievementRes(
        String code,
        String name,
        String description,
        String reward,
        boolean isAchieved,
        LocalDateTime achievedAt
) {}