package com.back.together02be.ranking.repository;

import com.back.together02be.ranking.entity.Ranking;
import com.back.together02be.ranking.entity.RankingSnapshotType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface RankingRepository extends JpaRepository<Ranking, Long> {

    // 타입과 날짜로 랭킹 목록을 조회한다.
    @Query("""
            select r
            from Ranking r
            where r.snapshotType = :type
              and r.snapshotDate = :date
            """)
    List<Ranking> findRankings(RankingSnapshotType type, LocalDate date, Sort sort);

    // 해당 날짜의 랭킹 존재 여부를 확인한다.
    @Query("""
            select (count(r) > 0)
            from Ranking r
            where r.snapshotType = :type
              and r.snapshotDate = :date
            """)
    boolean existsRanking(RankingSnapshotType type, LocalDate date);

    // DAILY 재생성을 위해 기존 랭킹을 삭제한다.
    @Modifying
    @Query("""
            delete
            from Ranking r
            where r.snapshotType = :type
              and r.snapshotDate = :date
            """)
    void deleteRankings(RankingSnapshotType type, LocalDate date);
}