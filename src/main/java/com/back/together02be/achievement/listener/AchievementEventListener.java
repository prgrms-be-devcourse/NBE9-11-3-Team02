package com.back.together02be.achievement.listener;

import com.back.together02be.achievement.entity.Achievement;
import com.back.together02be.achievement.entity.UserAchievement;
import com.back.together02be.achievement.event.TradeCompletedEvent;
import com.back.together02be.achievement.repository.AchievementRepository;
import com.back.together02be.achievement.repository.UserAchievementRepository;
import com.back.together02be.achievement.rule.AchievementRule;
import com.back.together02be.users.entity.Users;
import com.back.together02be.users.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AchievementEventListener {

    // 인터페이스를 구현한 모든 빈을 리스트로 자동 주입
    private final List<AchievementRule> rules; // 조건 로직들 자동 주입
    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UsersRepository usersRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTradeCompleted(TradeCompletedEvent event) {
        for (AchievementRule rule : rules) {
            String targetCode = rule.getTargetAchievementCode();

            // 이미 달성한 업적인지 DB 확인 (중복 지급 방지)
            boolean alreadyAchieved = userAchievementRepository
                    .existsByUsersIdAndAchievement_Code(event.getUserId(), targetCode);

            if (alreadyAchieved) {
                continue;
            }


            if (rule.isSatisfied(event)) {

                // 업적이 없으면 새로 생성하여 저장
                Achievement achievementMeta = achievementRepository.findByCode(targetCode);

                if (achievementMeta == null) {
                    achievementMeta = achievementRepository.save(
                            new Achievement(
                                    targetCode,
                                    rule.getDefaultName(),
                                    rule.getDefaultDescription()
                            )
                    );
                }

                Users user = usersRepository.getReferenceById(event.getUserId());

                UserAchievement newRecord = new UserAchievement(user, achievementMeta);
                userAchievementRepository.save(newRecord);

                log.info("업적 달성! 유저ID: {}, 업적명: {}",
                        event.getUserId(), achievementMeta.getName());
            }
        }
    }
}