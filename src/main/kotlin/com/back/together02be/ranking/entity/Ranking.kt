package com.back.together02be.ranking.entity

import com.back.together02be.global.entity.BaseEntity
import com.back.together02be.users.entity.Users
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(
    indexes = [
        Index(
            name = "idx_ranking_type_date",
            columnList = "snapshotType, snapshotDate"
        )
    ]
)
class Ranking(

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: Users,

    @Column(nullable = false)
    val rankingPosition: Int,

    @Column(nullable = false, precision = 10, scale = 2)
    val profitRate: BigDecimal,

    @Column(nullable = false)
    val totalAsset: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val snapshotType: RankingSnapshotType,

    @Column(nullable = false)
    val snapshotDate: LocalDate

) : BaseEntity()