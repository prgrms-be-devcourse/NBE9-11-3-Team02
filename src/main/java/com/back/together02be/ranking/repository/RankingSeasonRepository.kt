package com.back.together02be.ranking.repository

import com.back.together02be.ranking.entity.RankingSeason
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface RankingSeasonRepository : JpaRepository<RankingSeason, Long> {

// TODO: Phase 3(Service) 코틀린 전환 시, 반환 타입을 RankingSeason?로 변경하고
    //       GlobalExceptionHandler + 확장 함수(Extension Function) 조합으로 예외 처리 리팩토링 예정
    fun findByUserIdAndActiveTrue(userId: Long): Optional<RankingSeason>

    fun findByActiveTrue(): List<RankingSeason>
}