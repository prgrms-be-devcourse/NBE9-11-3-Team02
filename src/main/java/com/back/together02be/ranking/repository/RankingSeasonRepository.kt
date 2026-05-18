package com.back.together02be.ranking.repository

import com.back.together02be.ranking.entity.RankingSeason
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface RankingSeasonRepository : JpaRepository<RankingSeason, Long> {

    fun findByUserIdAndActiveTrue(userId: Long): RankingSeason?

    fun findByActiveTrue(): List<RankingSeason>
}