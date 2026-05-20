package com.back.together02be.ranking.service

import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.entity.UserStock
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.service.RealTimeStockPriceStore
import org.springframework.stereotype.Component

@Component
class RankingAssetCalculator(
    private val userStockRepository: UserStockRepository,
    private val realTimeStockPriceStore: RealTimeStockPriceStore
) {

    // 유저의 현재 총자산을 계산한다.
    fun calculateTotalAsset(userAccount: UserAccount): Long {
        val userStocks = userStockRepository.findAllByUsersId(userAccount.users.id)

        val stockEvaluationAmount = userStocks.sumOf { calculateStockEvaluationAmount(it) }

        return userAccount.deposit + stockEvaluationAmount
    }

    // 보유 종목 1건의 평가금액을 계산한다.
    fun calculateStockEvaluationAmount(userStock: UserStock): Long {
        val stockCode = userStock.stock.stockCode
        val realtimeStockPrice = realTimeStockPriceStore.get(stockCode)

        val currentPrice = extractCurrentPrice(realtimeStockPrice, userStock.averagePrice)

        return currentPrice * userStock.quantity
    }

    fun extractCurrentPrice(realtimeStockPrice: RealtimeStockPrice?, fallbackPrice: Long): Long {
        return realtimeStockPrice?.price
            ?.takeIf { it.isNotBlank() }
            ?.toLongOrNull()
            ?: fallbackPrice
    }
}