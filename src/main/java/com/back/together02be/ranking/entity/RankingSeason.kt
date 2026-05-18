package com.back.together02be.ranking.entity
import com.back.together02be.global.entity.BaseEntity
import com.back.together02be.users.entity.Users
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(
    indexes = [
        Index(
            name = "idx_ranking_season_user_active",
            columnList = "user_id, active"
        )
    ]
)
class RankingSeason(

    // Kotlin 전환 포인트:
    // 한 시즌 기준 자산은 반드시 특정 유저에 속해야 하므로 non-null Users로 선언한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: Users,

    // Kotlin 전환 포인트:
    // 기준 자산과 시작일은 시즌 생성 시 반드시 필요한 값이므로 non-null 타입으로 선언한다.
    @Column(nullable = false)
    val baseAsset: Long,

    @Column(nullable = false)
    val startDate: LocalDate

) : BaseEntity() {

    // Kotlin 전환 포인트:
    // 종료일은 진행 중인 시즌에서는 없을 수 있으므로 nullable 타입으로 유지한다.
    @Column
    var endDate: LocalDate? = null
        protected set

    // Kotlin 전환 포인트:
    // 활성 여부는 외부에서 직접 변경하지 못하도록 protected set으로 제한하고,
    // close() 메서드를 통해서만 상태를 변경하게 한다.
    @Column(nullable = false)
    var active: Boolean = true
        protected set

    // Kotlin 전환 포인트:
    // 상태 변경 메서드는 의미 있는 도메인 행위로 유지한다.
    // nullable이 아닌 LocalDate를 받아 종료일이 없는 종료 상태가 만들어지지 않도록 한다.
    fun close(endDate: LocalDate) {
        this.endDate = endDate
        this.active = false
    }
}