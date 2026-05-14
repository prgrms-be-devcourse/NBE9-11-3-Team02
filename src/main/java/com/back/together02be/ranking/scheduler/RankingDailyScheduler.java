package com.back.together02be.ranking.scheduler;

import com.back.together02be.ranking.service.RankingSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class RankingDailyScheduler {

    private final RankingSnapshotService rankingSnapshotService;

    // 매일 00시에 DAILY 랭킹을 생성한다.
//    @Scheduled(cron = "0 0 0 * * *")
    @Scheduled(fixedRate = 60000)
    public void generateDailyRanking() {
        LocalDate today = LocalDate.now();
        rankingSnapshotService.createDailySnapshot(today);

        log.info("Scheduled DAILY ranking created: {}", today);
    }
}