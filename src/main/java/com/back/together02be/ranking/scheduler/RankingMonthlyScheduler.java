package com.back.together02be.ranking.scheduler;

import com.back.together02be.ranking.service.RankingSeasonService;
import com.back.together02be.ranking.service.RankingSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class RankingMonthlyScheduler {

    private final RankingSnapshotService rankingSnapshotService;
    private final RankingSeasonService rankingSeasonService;

    // 매달 말일 23:59:59에 MONTHLY 랭킹을 생성한다.
    @Scheduled(cron = "59 59 23 L * *")
    public void generateMonthlyRanking() {
        LocalDate today = LocalDate.now();
        rankingSnapshotService.createMonthlySnapshot(today);

        log.info("Scheduled MONTHLY ranking created: {}", today);
    }

    // 매월 1일 00:00:00에 시즌 기준 자산을 리셋한다.
    @Scheduled(cron = "0 0 0 1 * *")
    public void resetSeason() {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate nextStartDate = LocalDate.now();

        rankingSeasonService.resetSeason(endDate, nextStartDate);

        log.info("Scheduled season reset: {} -> {}", endDate, nextStartDate);
    }
}
