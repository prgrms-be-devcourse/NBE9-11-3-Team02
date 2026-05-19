package com.back.together02be.achievement.service

import com.back.together02be.achievement.dto.AchievementRes
import com.back.together02be.achievement.repository.AchievementRepository
import com.back.together02be.achievement.repository.UserAchievementRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AchievementService(
    private val achievementRepository: AchievementRepository,
    private val userAchievementRepository: UserAchievementRepository
) {

    fun getUserAchievements(userId: Long): List<AchievementRes> {
        // 1. 시스템의 모든 업적 메타데이터 조회
        val allAchievements = achievementRepository.findAll()

        // 2. 사용자의 달성 기록 조회 및 Map으로 변환
        // Java의 stream().collect(Collectors.toMap(...)) 대신 associateBy 활용
        val userAchievedMap = userAchievementRepository.findByUsersId(userId)
            .associateBy { it.achievement.code }

        // 3. 전체 목록을 순회하며 달성 여부 매핑
        return allAchievements.map { achievement ->
            // Map에서 값을 한 번만 꺼내어 변수에 저장 (null일 수 있음)
            val achievedRecord = userAchievedMap[achievement.code]

            // 이름 있는 인자(Named Arguments)를 사용하여 가독성 향상
            AchievementRes(
                code = achievement.code,
                name = achievement.name,
                description = achievement.description,
                isAchieved = achievedRecord != null, // 객체가 존재하면 true
                achievedAt = achievedRecord?.createdAt // 널 안전 호출 연산자(?.)로 에러 방지
            )
        }
    }
}