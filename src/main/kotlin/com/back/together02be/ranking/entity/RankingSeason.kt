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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: Users,

    @Column(nullable = false)
    val baseAsset: Long,

    @Column(nullable = false)
    val startDate: LocalDate

) : BaseEntity() {

    @Column
    var endDate: LocalDate? = null
        protected set

    @Column(nullable = false)
    var active: Boolean = true
        protected set

    fun close(endDate: LocalDate) {
        this.endDate = endDate
        this.active = false
    }
}