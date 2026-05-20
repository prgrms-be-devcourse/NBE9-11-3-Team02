package com.back.together02be.ranking.controller

import com.back.together02be.ranking.dto.response.RankingRes
import com.back.together02be.ranking.service.RankingSeasonService
import com.back.together02be.ranking.service.RankingService
import com.back.together02be.ranking.service.RankingSnapshotService
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/rankings")
class RankingController(
    private val rankingService: RankingService,
    private val rankingSnapshotService: RankingSnapshotService,
    private val rankingSeasonService: RankingSeasonService
) {

    @GetMapping
    fun getRankings(): List<RankingRes> {
        return rankingService.getDailyRankings()
    }

    @PostMapping("/snapshots/daily")
    fun createDailySnapshot(@RequestParam(required = false) snapshotDate: LocalDate?): String {
        val targetDate = snapshotDate ?: LocalDate.now()
        rankingSnapshotService.createDailySnapshot(targetDate)

        // 코틀린의 문자열 템플릿($)을 활용해 + 연산자를 제거
        return "DAILY 랭킹 생성 완료: $targetDate"
    }

    @PostMapping("/snapshots/monthly")
    fun createMonthlySnapshot(@RequestParam snapshotDate: LocalDate): String {
        rankingSnapshotService.createMonthlySnapshot(snapshotDate)
        return "MONTHLY 랭킹 생성 완료: $snapshotDate"
    }

    @GetMapping("/monthly")
    fun getMonthlyRankings(@RequestParam snapshotDate: LocalDate): List<RankingRes> {
        return rankingService.getMonthlyRankings(snapshotDate)
    }

    @PostMapping("/season/start")
    fun startSeason(@RequestParam(required = false) startDate: LocalDate?): String {
        val targetDate = startDate ?: LocalDate.now()
        rankingSeasonService.startSeason(targetDate)
        return "시즌 시작 완료: $targetDate"
    }

    @PostMapping("/season/reset")
    fun resetSeason(
        @RequestParam endDate: LocalDate,
        @RequestParam nextStartDate: LocalDate
    ): String {
        rankingSeasonService.resetSeason(endDate, nextStartDate)
        return "시즌 리셋 완료: $endDate -> $nextStartDate"
    }
}