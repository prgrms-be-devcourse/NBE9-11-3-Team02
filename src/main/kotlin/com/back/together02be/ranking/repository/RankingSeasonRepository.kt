package com.back.together02be.ranking.repository

import com.back.together02be.ranking.entity.RankingSeason
import org.springframework.data.jpa.repository.JpaRepository

interface RankingSeasonRepository : JpaRepository<RankingSeason, Long> {

    // GlobalExceptionHandler 예외 처리 리팩토링 예정
    fun findByUserIdAndActiveTrue(userId: Long): RankingSeason?

    fun findByActiveTrue(): List<RankingSeason>
}