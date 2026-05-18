package com.back.together02be.achievement.listener

import com.back.together02be.achievement.entity.Achievement
import com.back.together02be.achievement.entity.UserAchievement
import com.back.together02be.achievement.event.TradeCompletedEvent
import com.back.together02be.achievement.repository.AchievementRepository
import com.back.together02be.achievement.repository.UserAchievementRepository
import com.back.together02be.achievement.rule.AchievementRule
import com.back.together02be.users.repository.UsersRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class AchievementEventListener(
    private val rules: List<AchievementRule>,
    private val achievementRepository: AchievementRepository,
    private val userAchievementRepository: UserAchievementRepository,
    private val usersRepository: UsersRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleTradeCompleted(event: TradeCompletedEvent) {
        rules.forEach { rule ->
            val targetCode = rule.targetAchievementCode
            val alreadyAchieved = userAchievementRepository
                .existsByUsersIdAndAchievement_Code(event.userId, targetCode)

            if (!alreadyAchieved && rule.isSatisfied(event)) {

                // 엘비스 연산자(?:)를 사용하여 Optional.orElseGet 대체
                val achievementMeta = achievementRepository.findByCode(targetCode)
                    ?: achievementRepository.save(
                        Achievement(
                            code = targetCode,
                            name = rule.defaultName,
                            description = rule.defaultDescription
                        )
                    )

                val user = usersRepository.getReferenceById(event.userId)
                val newRecord = UserAchievement(users = user, achievement = achievementMeta)
                userAchievementRepository.save(newRecord)

                log.info("업적 달성! 유저ID: {}, 업적명: {}", event.userId, achievementMeta.name)
            }
        }
    }
}