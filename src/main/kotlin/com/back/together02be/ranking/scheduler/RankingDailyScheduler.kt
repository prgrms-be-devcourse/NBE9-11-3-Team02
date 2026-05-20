package com.back.together02be.ranking.scheduler

import com.back.together02be.ranking.service.RankingSnapshotService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class RankingDailyScheduler(
    private val rankingSnapshotService: RankingSnapshotService
) {
    // Lombok의 @Slf4j 대신 코틀린 방식의 로거 선언
    private val log = LoggerFactory.getLogger(this::class.java)

    // 매일 00시에 DAILY 랭킹을 생성한다.
    // @Scheduled(cron = "0 0 0 * * *")
    @Scheduled(fixedRate = 60000)
    fun generateDailyRanking() {
        val today = LocalDate.now()
        rankingSnapshotService.createDailySnapshot(today)

        log.info("Scheduled DAILY ranking created: {}", today)
    }
}