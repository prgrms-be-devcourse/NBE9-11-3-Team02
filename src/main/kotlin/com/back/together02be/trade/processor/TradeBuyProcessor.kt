package com.back.together02be.trade.processor

import com.back.together02be.achievement.event.TradeCompletedEvent
import com.back.together02be.asset.entity.UserStock
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.global.idempotency.IdempotencyKeyRepository
import com.back.together02be.stock.repository.StockRepository
import com.back.together02be.stock.service.RealTimeStockPriceStore
import com.back.together02be.trade.dto.BuyReq
import com.back.together02be.trade.dto.BuyRes
import com.back.together02be.trade.entity.Trade
import com.back.together02be.trade.repository.TradeRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityNotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Component
class TradeBuyProcessor(
    private val stockPriceStore: RealTimeStockPriceStore,
    private val userAccountRepository: UserAccountRepository,
    private val userStockRepository: UserStockRepository,
    private val stockRepository: StockRepository,
    private val tradeRepository: TradeRepository,
    private val idempotencyKeyRepository: IdempotencyKeyRepository,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun processBuy(userId: Long, idempotencyKey: String, request: BuyReq): BuyRes {
        val stock = stockRepository.findById(request.stockId!!)
            .orElseThrow { EntityNotFoundException("주식 정보가 없습니다.") }

        val stockPrice = stockPriceStore.get(stock.stockCode)
            ?: throw EntityNotFoundException("현재가 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.")

        val price = stockPrice.price.toLong()

        val tradeTime = stockPrice.tradeTime
        if (!tradeTime.isNullOrEmpty()) {
            val tradeDateTime = LocalDateTime.of(
                LocalDate.now(),
                LocalTime.parse(tradeTime, DateTimeFormatter.ofPattern("HHmmss"))
            )
            if (Duration.between(tradeDateTime, LocalDateTime.now()).seconds > 10) {
                throw IllegalStateException("가격 정보가 오래되었습니다. 잠시 후 다시 시도해주세요.")
            }
        }

        if (price.toDouble() > request.expectedPrice!! * 1.02) {
            throw IllegalStateException(
                "가격이 너무 올랐습니다. (예상: %,d원 / 현재: %,d원)".format(request.expectedPrice, price)
            )
        }

        val amount = price * request.quantity!!

        val updated = userAccountRepository.decreaseDepositIfSufficient(userId, amount)
        if (updated == 0) {
            val accountForMsg = userAccountRepository.findByUsersId(userId)
                .orElseThrow { EntityNotFoundException("계좌 정보가 없습니다.") }
            throw IllegalStateException(
                "잔고가 부족합니다. (필요: %,d원 / 보유: %,d원)".format(amount, accountForMsg.deposit)
            )
        }

        val account = userAccountRepository.findByUsersIdWithLock(userId)
            .orElseThrow { EntityNotFoundException("계좌 정보가 없습니다.") }

        val userStock = userStockRepository.findByUsersIdAndStockId(userId, request.stockId).orElse(null)
        if (userStock == null) {
            userStockRepository.save(UserStock(account.users, stock, request.quantity, price))
        } else {
            userStock.updateOnBuy(request.quantity, price)
        }

        val trade = Trade.buy(account.users, stock, request.quantity, price)
        tradeRepository.save(trade)

        eventPublisher.publishEvent(TradeCompletedEvent(userId, amount, account.totalPurchase))

        val result = BuyRes(
            tradeId = trade.id,
            stockName = stock.stockName,
            quantity = request.quantity,
            price = price,
            amount = amount,
            remainingDeposit = account.deposit,
        )

        idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
            .ifPresent { it.storeResponse(objectMapper.writeValueAsString(result)) }

        return result
    }
}
