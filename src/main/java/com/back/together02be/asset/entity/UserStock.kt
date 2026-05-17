package com.back.together02be.asset.entity

import com.back.together02be.global.entity.BaseEntity
import com.back.together02be.stock.entity.Stock
import com.back.together02be.users.entity.Users
import jakarta.persistence.*

@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["users_id", "stock_id"])])
class UserStock(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    val users: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    val stock: Stock?, // 자바에서 nullable 설정이 없었으므로 안전하게 Nullable 처리 (필요시 Stock으로 변경 가능)

    @Column(nullable = false)
    var quantity: Long,

    @Column(nullable = false)
    var averagePrice: Long
) : BaseEntity() {

    // 매수 시 수량 증가 + 평균매입가 재계산
    fun updateOnBuy(buyQuantity: Long, buyPrice: Long) {
        val newTotalCost = (this.quantity * this.averagePrice) + (buyQuantity * buyPrice)
        this.quantity += buyQuantity
        this.averagePrice = newTotalCost / this.quantity
    }

    fun updateQuantity(newQuantity: Long) {
        require(newQuantity >= 0) { "보유 수량은 0보다 작을 수 없습니다." }
        this.quantity = newQuantity
    }
}