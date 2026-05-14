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

    // 인터페이스를 구현한 모든 빈을 리스트로 자동 주입받습니다.
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

            // 1. 이미 달성한 업적인지 DB 확인 (중복 지급 방지)
            boolean alreadyAchieved = userAchievementRepository
                    .existsByUsersIdAndAchievement_Code(event.userId(), targetCode);

            if (alreadyAchieved) {
                continue;
            }

            // 2. 달성하지 않았다면 객체의 로직(if문) 평가
            if (rule.isSatisfied(event)) {

                // 업적이 없으면 새로 생성하여 저장 (Get or Create 패턴)
                Achievement achievementMeta = achievementRepository.findByCode(targetCode)
                        .orElseGet(() -> achievementRepository.save(
                                Achievement.builder()
                                        .code(targetCode)
                                        .name(rule.getDefaultName()) // 인터페이스에서 가져옴
                                        .description(rule.getDefaultDescription())
                                        .reward("기본 보상") // 또는 기본값 설정
                                        .build()
                        ));

                Users user = usersRepository.getReferenceById(event.userId());

                UserAchievement newRecord = new UserAchievement(user, achievementMeta);
                userAchievementRepository.save(newRecord);

                log.info("업적 달성! 유저ID: {}, 업적명: {}, 보상: {}",
                        event.userId(), achievementMeta.getName(), achievementMeta.getReward());

                // 필요하다면 여기서 프론트엔드로 알림(SSE, WebSocket) 전송
            }
        }
    }
}