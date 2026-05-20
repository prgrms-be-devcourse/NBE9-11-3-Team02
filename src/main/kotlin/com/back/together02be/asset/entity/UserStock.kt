package com.back.together02be.asset.entity

import com.back.together02be.global.entity.BaseEntity
import com.back.together02be.stock.entity.Stock
import com.back.together02be.users.entity.Users
import jakarta.persistence.*
import lombok.Getter
import lombok.NoArgsConstructor

@Entity
@Table(uniqueConstraints = [UniqueConstraint(columnNames = ["users_id", "stock_id"])])
class UserStock(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    val users: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    val stock: Stock, // 보유한 주식 종목 자체는 변경되지 않으므로 val

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