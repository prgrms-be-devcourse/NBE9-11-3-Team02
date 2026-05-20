package com.back.together02be.trade.entity

import com.back.together02be.global.entity.BaseEntity
import com.back.together02be.stock.entity.Stock
import com.back.together02be.users.entity.Users
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
class Trade(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    val users: Users,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id", nullable = false)
    val stock: Stock,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: TradeType,

    @Column(nullable = false)
    val quantity: Long,

    @Column(nullable = false)
    val price: Long,

    val profit: Long?,
) : BaseEntity() {

    @Column(nullable = false)
    val amount: Long = quantity * price

    @Column(name = "traded_at", nullable = false)
    val tradedAt: LocalDateTime = LocalDateTime.now()

    companion object {
        @JvmStatic
        fun buy(users: Users, stock: Stock, quantity: Long, price: Long): Trade =
            Trade(users, stock, TradeType.BUY, quantity, price, null)

        @JvmStatic
        fun sell(users: Users, stock: Stock, quantity: Long, price: Long, profit: Long): Trade =
            Trade(users, stock, TradeType.SELL, quantity, price, profit)
    }
}
