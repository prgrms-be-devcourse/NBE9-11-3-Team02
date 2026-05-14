package com.back.together02be.ranking.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.back.together02be.global.entity.BaseEntity;
import com.back.together02be.users.entity.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(
        indexes = {
                @Index(name = "idx_ranking_type_date", columnList = "snapshotType, snapshotDate")
        }
)
public class Ranking extends BaseEntity {

    // 한 유저는 날짜별/타입별로 여러 랭킹 기록을 가질 수 있다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    // 랭킹 순위.
    @Column(nullable = false)
    private Integer rankingPosition;

    // 수익률은 소수점 계산을 위해 BigDecimal을 사용한다.
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal profitRate;

    // 현재 총자산이다.
    @Column(nullable = false)
    private Long totalAsset;

    // DAILY / MONTHLY 랭킹을 구분한다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RankingSnapshotType snapshotType;

    // 랭킹 기준 날짜이다.
    @Column(nullable = false)
    private LocalDate snapshotDate;

    public Ranking(
            Users user,
            Integer rankingPosition,
            BigDecimal profitRate,
            Long totalAsset,
            RankingSnapshotType snapshotType,
            LocalDate snapshotDate
    ) {
        this.user = user;
        this.rankingPosition = rankingPosition;
        this.profitRate = profitRate;
        this.totalAsset = totalAsset;
        this.snapshotType = snapshotType;
        this.snapshotDate = snapshotDate;
    }
}