package com.back.together02be.ranking.controller;

import com.back.together02be.ranking.dto.response.RankingRes;
import com.back.together02be.ranking.service.RankingSeasonService;
import com.back.together02be.ranking.service.RankingService;
import com.back.together02be.ranking.service.RankingSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;
    private final RankingSnapshotService rankingSnapshotService;
    private final RankingSeasonService rankingSeasonService;

    // 메인 랭킹 조회 API이다.
    @GetMapping
    public List<RankingRes> getRankings() {
        return rankingService.getDailyRankings();
    }

    // DAILY 랭킹을 생성한다.
    @PostMapping("/snapshots/daily")
    public String createDailySnapshot(@RequestParam(required = false) LocalDate snapshotDate) {
        LocalDate targetDate = (snapshotDate != null) ? snapshotDate : LocalDate.now();
        rankingSnapshotService.createDailySnapshot(targetDate);
        return "DAILY 랭킹 생성 완료: " + targetDate;
    }

    // MONTHLY 랭킹을 생성한다.
    @PostMapping("/snapshots/monthly")
    public String createMonthlySnapshot(@RequestParam LocalDate snapshotDate) {
        rankingSnapshotService.createMonthlySnapshot(snapshotDate);
        return "MONTHLY 랭킹 생성 완료: " + snapshotDate;
    }

    // 특정 날짜의 MONTHLY 랭킹을 조회한다.
    @GetMapping("/monthly")
    public List<RankingRes> getMonthlyRankings(@RequestParam LocalDate snapshotDate) {
        return rankingService.getMonthlyRankings(snapshotDate);
    }

    // 시즌 시작 시 기준 자산을 생성한다.
    @PostMapping("/season/start")
    public String startSeason(@RequestParam(required = false) LocalDate startDate) {
        LocalDate targetDate = (startDate != null) ? startDate : LocalDate.now();
        rankingSeasonService.startSeason(targetDate);
        return "시즌 시작 완료: " + targetDate;
    }

    // 시즌 종료 후 다음 시즌 기준 자산을 다시 생성한다.
    @PostMapping("/season/reset")
    public String resetSeason(
            @RequestParam LocalDate endDate,
            @RequestParam LocalDate nextStartDate
    ) {
        rankingSeasonService.resetSeason(endDate, nextStartDate);
        return "시즌 리셋 완료: " + endDate + " -> " + nextStartDate;
    }
}