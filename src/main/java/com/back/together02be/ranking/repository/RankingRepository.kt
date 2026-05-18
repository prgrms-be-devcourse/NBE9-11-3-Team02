package com.back.together02be.ranking.repository

import com.back.together02be.ranking.entity.Ranking
import com.back.together02be.ranking.entity.RankingSnapshotType
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface RankingRepository : JpaRepository<Ranking, Long> {

    // Kotlin 전환 포인트:
    // type, date, sort는 랭킹 조회에 반드시 필요한 조건이므로 nullable이 아닌 타입으로 선언한다.
    // 반환값도 null이 아니라 빈 리스트로 표현하는 것이 자연스럽다.
    @Query(
        """
            select r
            from Ranking r
            where r.snapshotType = :type
              and r.snapshotDate = :date
        """
    )
    fun findRankings(
        @Param("type") type: RankingSnapshotType,
        @Param("date") date: LocalDate,
        sort: Sort
    ): List<Ranking>

    // Kotlin 전환 포인트:
    // 존재 여부는 항상 true/false로 판단 가능하므로 Boolean non-null로 유지한다.
    @Query(
        """
            select (count(r) > 0)
            from Ranking r
            where r.snapshotType = :type
              and r.snapshotDate = :date
        """
    )
    fun existsRanking(
        @Param("type") type: RankingSnapshotType,
        @Param("date") date: LocalDate
    ): Boolean

    // Kotlin 전환 포인트:
    // 삭제 조건인 type/date는 필수 값이므로 nullable을 제거한다.
    @Modifying
    @Query(
        """
            delete
            from Ranking r
            where r.snapshotType = :type
              and r.snapshotDate = :date
        """
    )
    fun deleteRankings(
        @Param("type") type: RankingSnapshotType,
        @Param("date") date: LocalDate
    )
}