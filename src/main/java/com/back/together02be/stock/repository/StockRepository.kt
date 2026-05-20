package com.back.together02be.stock.repository

import com.back.together02be.stock.entity.Stock
import org.springframework.data.jpa.repository.JpaRepository

interface StockRepository : JpaRepository<Stock, Long> {

    override fun findAll(): List<Stock>

    // todo optional 확장함수로 처리
    fun findByStockCode(stockCode: String): Stock?

    fun existsByStockCode(stockCode: String): Boolean
}
