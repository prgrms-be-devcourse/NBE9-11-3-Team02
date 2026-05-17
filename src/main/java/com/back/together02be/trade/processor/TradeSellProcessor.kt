package com.back.together02be.trade.processor

import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.repository.StockRepository
import com.back.together02be.stock.service.RealTimeStockPriceStore
import com.back.together02be.trade.dto.request.TradeSellReq
import com.back.together02be.trade.dto.response.TradeSellRes
import com.back.together02be.trade.entity.Trade
import com.back.together02be.trade.repository.TradeRepository
import com.back.together02be.trade.util.MarketTimeValidator
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Component
class TradeSellProcessor(
    private val stockPriceStore: RealTimeStockPriceStore,
    private val userAccountRepository: UserAccountRepository,
    private val userStockRepository: UserStockRepository,
    private val stockRepository: StockRepository,
    private val tradeRepository: TradeRepository
) {

    companion object {
        private val SELL_TOLERANCE_RATE = BigDecimal("0.98")
    }

    // 10초 이상 지연시 예외처리
    private fun isStale(stockPrice: RealtimeStockPrice, limitSeconds: Int): Boolean {
        val tradeTimeStr = stockPrice.tradeTime ?: return true

        val formatter = DateTimeFormatter.ofPattern("HHmmss")
        val tradeTime = LocalTime.parse(tradeTimeStr, formatter)

        // 현재 날짜와 매칭
        val tradeDateTime = LocalDateTime.of(LocalDate.now(), tradeTime)
        val now = LocalDateTime.now()

        return ChronoUnit.SECONDS.between(tradeDateTime, now) > limitSeconds
    }

    @Transactional
    fun processSell(userId: Long, request: TradeSellReq): TradeSellRes {
        // 0. 장 마감 조회
        MarketTimeValidator.validateMarketOpen()

        // 1. 주식 정보 조회 및 보유 주식 조회
        val stock = stockRepository.findById(request.stockId)
            .orElseThrow { EntityNotFoundException("주식 정보가 없습니다.") }

        val userStock = userStockRepository.findByUsersIdAndStockId(userId, request.stockId)
            .orElseThrow { EntityNotFoundException("보유하지 않은 주식입니다.") }

        // 2. 현재가 조회 — KIS WebSocket 수신 후 RealTimeStockPriceStore에 저장된 실시간 가격
        val stockPrice = stockPriceStore.get(stock.stockCode)
            ?: throw EntityNotFoundException("현재가 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.")

        if (isStale(stockPrice, 10)) {
            throw IllegalStateException("시세 정보가 10초 이상 지연되었습니다. 거래가 불가능합니다.")
        }
        val price = stockPrice.price.toLong()

        // 슬리피지 검증 0.98을 BigDecimal로 표현
        val minPrice = BigDecimal.valueOf(request.expectedPrice)
            .multiply(SELL_TOLERANCE_RATE)
            .setScale(0, RoundingMode.FLOOR)

        if (BigDecimal.valueOf(price) < minPrice) {
            throw IllegalStateException("가격 변동폭이 커서 매도 주문이 거부되었습니다.")
        }

        // 3. 수량 검증
        val updatedRows = userStockRepository.updateQuantity(userId, request.stockId, request.quantity)
        if (updatedRows == 0) {
            throw IllegalStateException("보유 수량이 부족합니다.")
        }

        // 5. 수익 / 금액 계산
        val profit = (price - userStock.averagePrice) * request.quantity
        val amount = price * request.quantity
        val purchaseAmount = userStock.averagePrice * request.quantity

        // 6. 예수금 증가
        val accountUpdated = userAccountRepository.updateDepositAndPurchase(userId, amount, purchaseAmount)
        if (accountUpdated == 0) {
            throw IllegalStateException("계좌 정보 업데이트에 실패했습니다.")
        }

        // 7. 수량 차감 및 전량 매도시 삭제
        if (userStock.quantity == request.quantity) {
            userStockRepository.deleteByUserAndStock(userId, request.stockId)
        }

        // 8. 거래 내역 저장 (account는 여기서 조회)
        val account = userAccountRepository.findByUsersId(userId)
            .orElseThrow { EntityNotFoundException("존재하지 않는 계좌입니다.") }

        // 8. 거래 내역 저장
        val trade = Trade.sell(account.users, stock, request.quantity, price, profit)
        tradeRepository.save(trade)

        return TradeSellRes(
            tradeId = trade.id,
            stockName = stock.stockName,
            quantity = request.quantity,
            price = price,
            amount = amount,
            remainingDeposit = account.deposit
        )
    }
}