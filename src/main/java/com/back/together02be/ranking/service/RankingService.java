package com.back.together02be.ranking.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.back.together02be.ranking.dto.response.RankingRes;
import com.back.together02be.ranking.entity.Ranking;
import com.back.together02be.ranking.entity.RankingSnapshotType;
import com.back.together02be.ranking.repository.RankingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final RankingRepository rankingRepository;

    // 메인 랭킹은 오늘 기준 DAILY top5를 반환한다.
    public List<RankingRes> getDailyRankings() {
        LocalDate today = LocalDate.now();

        return rankingRepository.findRankings(
                        RankingSnapshotType.DAILY,
                        today,
                        Sort.by(Sort.Direction.ASC, "rankingPosition")
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 특정 월말 기준 MONTHLY 랭킹을 조회한다.
    public List<RankingRes> getMonthlyRankings(LocalDate snapshotDate) {
        return rankingRepository.findRankings(
                        RankingSnapshotType.MONTHLY,
                        snapshotDate,
                        Sort.by(Sort.Direction.ASC, "rankingPosition")
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 엔티티를 응답 DTO로 변환한다.
    private RankingRes toResponse(Ranking ranking) {
        return new RankingRes(
                ranking.getUser().getId(),
                ranking.getUser().getNickname(),
                ranking.getRankingPosition(),
                ranking.getProfitRate(),
                ranking.getTotalAsset()
        );
    }
}