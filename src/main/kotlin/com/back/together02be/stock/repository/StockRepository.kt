package com.back.together02be.stock.repository

import com.back.together02be.stock.entity.Stock
import org.springframework.data.jpa.repository.JpaRepository

interface StockRepository : JpaRepository<Stock, Long> {

    override fun findAll(): List<Stock>

    fun findByStockCode(stockCode: String): Stock?

    fun existsByStockCode(stockCode: String): Boolean
}
