package com.back.together02be.ranking.scheduler

import com.back.together02be.ranking.service.RankingSeasonService
import com.back.together02be.ranking.service.RankingSnapshotService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class RankingMonthlyScheduler(
    private val rankingSnapshotService: RankingSnapshotService,
    private val rankingSeasonService: RankingSeasonService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // 매달 말일 23:59:59에 MONTHLY 랭킹을 생성한다.
    @Scheduled(cron = "59 59 23 L * *")
    fun generateMonthlyRanking() {
        val today = LocalDate.now()
        rankingSnapshotService.createMonthlySnapshot(today)

        log.info("Scheduled MONTHLY ranking created: {}", today)
    }

    // 매월 1일 00:00:00에 시즌 기준 자산을 리셋한다.
    @Scheduled(cron = "0 0 0 1 * *")
    fun resetSeason() {
        val endDate = LocalDate.now().minusDays(1)
        val nextStartDate = LocalDate.now()

        rankingSeasonService.resetSeason(endDate, nextStartDate)

        log.info("Scheduled season reset: {} -> {}", endDate, nextStartDate)
    }
}