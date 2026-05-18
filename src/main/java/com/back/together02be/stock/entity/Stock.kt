package com.back.together02be.stock.entity

import com.back.together02be.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

@Entity
class Stock(

    @Column(nullable = false, unique = true)
    val stockCode: String,

    @Column(nullable = false)
    val stockName: String,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    val market: StockMarket

) : BaseEntity() 