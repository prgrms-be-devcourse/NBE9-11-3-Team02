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