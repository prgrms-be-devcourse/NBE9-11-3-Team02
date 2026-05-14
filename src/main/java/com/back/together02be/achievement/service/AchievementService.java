package com.back.together02be.achievement.service;

import com.back.together02be.achievement.dto.AchievementRes;
import com.back.together02be.achievement.entity.Achievement;
import com.back.together02be.achievement.entity.UserAchievement;
import com.back.together02be.achievement.repository.AchievementRepository;
import com.back.together02be.achievement.repository.UserAchievementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;

    public List<AchievementRes> getUserAchievements(Long userId) {
        // 1. 시스템의 모든 업적 메타데이터 조회
        List<Achievement> allAchievements = achievementRepository.findAll();

        // 2. 사용자의 달성 기록 조회 및 Map으로 변환 (빠른 조회를 위함)
        Map<String, UserAchievement> userAchievedMap = userAchievementRepository.findByUsersId(userId)
                .stream()
                .collect(Collectors.toMap(
                        ua -> ua.getAchievement().getCode(),
                        ua -> ua
                ));

        // 3. 전체 목록을 순회하며 달성 여부 매핑
        return allAchievements.stream()
                .map(achievement -> new AchievementRes(
                        achievement.getCode(),
                        achievement.getName(),
                        achievement.getDescription(),
                        achievement.getReward(),
                        userAchievedMap.containsKey(achievement.getCode()), // 달성 여부
                        userAchievedMap.containsKey(achievement.getCode()) ?
                                userAchievedMap.get(achievement.getCode()).getCreatedAt() : null // 달성 시간
                ))
                .collect(Collectors.toList());
    }
}