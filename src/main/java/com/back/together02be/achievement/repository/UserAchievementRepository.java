package com.back.together02be.achievement.repository;

import com.back.together02be.achievement.entity.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {
    boolean existsByUsersIdAndAchievement_Code(Long usersId, String targetCode);

    // 사용자의 모든 업적 달성 기록 조회 추가
    List<UserAchievement> findByUsersId(Long usersId);
}
