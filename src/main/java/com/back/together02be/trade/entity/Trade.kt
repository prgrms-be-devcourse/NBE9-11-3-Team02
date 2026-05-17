package com.back.together02be.trade.entity

import com.back.together02be.global.entity.BaseEntity
import com.back.together02be.stock.entity.Stock
import com.back.together02be.users.entity.Users
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.LocalDateTime

@Entity
class Trade private constructor(
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

    @Column(nullable = false)
    val amount: Long = quantity * price,

    val profit: Long? = null,

    @Column(name = "traded_at", nullable = false)
    val tradedAt: LocalDateTime = LocalDateTime.now()
) : BaseEntity() {

    // JPA가 프록시 객체를 생성할 때 필요한 기본 생성자 (Protected 수준)
    protected constructor() : this(
        users = null!!,
        stock = null!!,
        type = TradeType.BUY,
        quantity = 0L,
        price = 0L,
        amount = 0L,
        profit = null,
        tradedAt = LocalDateTime.now()
    )

    companion object {
        // 매수: profit은 무조건 null
        fun buy(users: Users, stock: Stock, quantity: Long, price: Long): Trade {
            return Trade(
                users = users,
                stock = stock,
                type = TradeType.BUY,
                quantity = quantity,
                price = price,
                profit = null
            )
        }

        // 매도: profit 필수
        fun sell(users: Users, stock: Stock, quantity: Long, price: Long, profit: Long): Trade {
            return Trade(
                users = users,
                stock = stock,
                type = TradeType.SELL,
                quantity = quantity,
                price = price,
                profit = profit
            )
        }
    }
}