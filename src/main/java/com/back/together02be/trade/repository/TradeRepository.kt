package com.back.together02be.trade.repository

import com.back.together02be.trade.entity.Trade
import org.springframework.data.jpa.repository.JpaRepository

interface TradeRepository : JpaRepository<Trade, Long>
