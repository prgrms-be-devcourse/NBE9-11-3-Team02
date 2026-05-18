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

    // Kotlin 전환 포인트:
    // Java Entity에서는 Lombok @Getter로 getter를 만들었지만,
    // Kotlin에서는 val/var 프로퍼티 자체가 getter를 제공하므로 Lombok을 제거한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: Users,

    // Kotlin 전환 포인트:
    // DB 컬럼이 nullable = false인 값은 Kotlin에서도 nullable 타입(?)이 아니라 non-null 타입으로 선언한다.
    // 이를 통해 랭킹 순위가 없는 Ranking 객체가 생성되는 것을 컴파일 단계에서 방지한다.
    @Column(nullable = false)
    val rankingPosition: Int,

    // Kotlin 전환 포인트:
    // 수익률은 null이 되면 랭킹 계산/응답 변환이 불가능하므로 BigDecimal로 명확히 선언한다.
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