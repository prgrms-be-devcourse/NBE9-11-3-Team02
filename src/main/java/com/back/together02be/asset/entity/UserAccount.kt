package com.back.together02be.asset.entity

import com.back.together02be.global.entity.BaseEntity
import com.back.together02be.users.entity.Users
import jakarta.persistence.*

@Entity
class UserAccount(
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false, unique = true)
    val users: Users,

    @Column(name = "total_purchase", nullable = false)
    var totalPurchase: Long,

    @Column(name = "deposit", nullable = false)
    var deposit: Long
) : BaseEntity() {

    // 비즈니스 로직 메서드들
    fun decreaseDeposit(amount: Long) {
        this.deposit -= amount
    }

    fun increaseTotalPurchase(amount: Long) {
        this.totalPurchase += amount
    }

    fun addDeposit(amount: Long) {
        this.deposit += amount // 누적 합산
    }

    fun subtractTotalPurchase(amount: Long) {
        this.totalPurchase = maxOf(0L, this.totalPurchase - amount)
    }
}