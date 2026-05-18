package com.back.together02be.ranking.service

import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.ranking.entity.Ranking
import com.back.together02be.ranking.entity.RankingSnapshotType
import com.back.together02be.ranking.repository.RankingRepository
import com.back.together02be.users.entity.Users
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class RankingSnapshotService(
    private val rankingRepository: RankingRepository,
    private val userAccountRepository: UserAccountRepository,
    private val rankingSeasonService: RankingSeasonService,
    private val rankingAssetCalculator: RankingAssetCalculator
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Transactional
    fun createDailySnapshot(snapshotDate: LocalDate) {
        rankingRepository.deleteRankings(RankingSnapshotType.DAILY, snapshotDate)

        val candidates = getTop5Candidates()
        saveRankings(candidates, RankingSnapshotType.DAILY, snapshotDate)

        log.info("DAILY ranking created: {}", snapshotDate)
    }

    @Transactional
    fun createMonthlySnapshot(snapshotDate: LocalDate) {
        check(!rankingRepository.existsRanking(RankingSnapshotType.MONTHLY, snapshotDate)) {
            "이미 해당 날짜의 MONTHLY 랭킹이 존재합니다."
        }

        val candidates = getTop5Candidates()
        saveRankings(candidates, RankingSnapshotType.MONTHLY, snapshotDate)

        log.info("MONTHLY ranking created: {}", snapshotDate)
    }

    private fun getTop5Candidates(): List<RankingCandidate> {
        val accounts = userAccountRepository.findAll()

        val candidates = accounts.map { account ->
            val user = account.users
            val totalAsset = rankingAssetCalculator.calculateTotalAsset(account)
            val season = rankingSeasonService.getActiveSeason(user.id)
            val profitRate = calculateProfitRate(totalAsset, season.baseAsset)

            RankingCandidate(user, totalAsset, profitRate)
        }

        return candidates.sortedWith(
            compareByDescending<RankingCandidate> { it.profitRate }
                .thenByDescending { it.totalAsset }
                .thenBy { it.user.id }
        ).take(5)
    }

    private fun calculateProfitRate(totalAsset: Long, baseAsset: Long): BigDecimal {
        if (baseAsset <= 0L) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        }

        return BigDecimal.valueOf(totalAsset - baseAsset)
            .multiply(BigDecimal("100"))
            .divide(BigDecimal.valueOf(baseAsset), 2, RoundingMode.HALF_UP)
    }

    private fun saveRankings(
        candidates: List<RankingCandidate>,
        snapshotType: RankingSnapshotType,
        snapshotDate: LocalDate
    ) {
        var rank = 1

        for (candidate in candidates) {
            val ranking = Ranking(
                candidate.user,
                rank++,
                candidate.profitRate,
                candidate.totalAsset,
                snapshotType,
                snapshotDate
            )

            rankingRepository.save(ranking)
        }
    }

    private data class RankingCandidate(
        val user: Users,
        val totalAsset: Long,
        val profitRate: BigDecimal
    )
}