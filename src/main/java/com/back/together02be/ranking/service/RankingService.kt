package com.back.together02be.ranking.service

import com.back.together02be.ranking.dto.response.RankingRes
import com.back.together02be.ranking.entity.Ranking
import com.back.together02be.ranking.entity.RankingSnapshotType
import com.back.together02be.ranking.repository.RankingRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class RankingService(
    private val rankingRepository: RankingRepository
) {

    fun getDailyRankings(): List<RankingRes> {
        val today = LocalDate.now()

        return rankingRepository.findRankings(
            RankingSnapshotType.DAILY,
            today,
            Sort.by(Sort.Direction.ASC, "rankingPosition")
        ).map { toResponse(it) }
    }

    fun getMonthlyRankings(snapshotDate: LocalDate): List<RankingRes> {
        return rankingRepository.findRankings(
            RankingSnapshotType.MONTHLY,
            snapshotDate,
            Sort.by(Sort.Direction.ASC, "rankingPosition")
        ).map { toResponse(it) }
    }

    private fun toResponse(ranking: Ranking): RankingRes {
        return RankingRes(
            ranking.user.id,
            ranking.user.nickname,
            ranking.rankingPosition,
            ranking.profitRate,
            ranking.totalAsset
        )
    }
}