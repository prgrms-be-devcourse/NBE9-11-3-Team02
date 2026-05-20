package com.back.together02be.ranking.service

import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.ranking.entity.RankingSeason
import com.back.together02be.ranking.repository.RankingSeasonRepository
import com.back.together02be.users.entity.Users
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class RankingSeasonService(
    private val rankingSeasonRepository: RankingSeasonRepository,
    private val userAccountRepository: UserAccountRepository,
    private val rankingAssetCalculator: RankingAssetCalculator
) {

    @Transactional
    fun startSeason(startDate: LocalDate) {
        val accounts = userAccountRepository.findAll()

        for (account in accounts) {
            val userId = account.users.id

            if (rankingSeasonRepository.findByUserIdAndActiveTrue(userId) != null) {
                continue
            }

            val totalAsset = rankingAssetCalculator.calculateTotalAsset(account)
            val season = RankingSeason(account.users, totalAsset, startDate)
            rankingSeasonRepository.save(season)
        }
    }

    @Transactional
    fun closeSeason(endDate: LocalDate) {
        val activeSeasons = rankingSeasonRepository.findByActiveTrue()

        activeSeasons.forEach { season ->
            season.close(endDate)
        }
    }

    @Transactional
    fun resetSeason(endDate: LocalDate, nextStartDate: LocalDate) {
        closeSeason(endDate)
        startSeason(nextStartDate)
    }

    @Transactional(readOnly = true)
    fun getActiveSeason(userId: Long): RankingSeason {
        return rankingSeasonRepository.findByUserIdAndActiveTrue(userId)
            ?: throw IllegalStateException("활성 시즌 정보가 없습니다. userId=$userId")
    }

    @Transactional
    fun createSeasonForUser(user: Users, startDate: LocalDate) {
        val exists = rankingSeasonRepository.findByUserIdAndActiveTrue(user.id) != null

        if (exists) {
            return
        }

        val account = userAccountRepository.findByUsersId(user.id)
            ?: throw IllegalStateException("계좌 정보가 없습니다. userId=${user.id}")

        val totalAsset = rankingAssetCalculator.calculateTotalAsset(account)
        val season = RankingSeason(user, totalAsset, startDate)
        rankingSeasonRepository.save(season)
    }
}