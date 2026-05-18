package com.back.together02be.trade.service

import com.back.together02be.global.exception.DuplicateRequestException
import com.back.together02be.global.idempotency.IdempotencyService
import com.back.together02be.trade.dto.BuyReq
import com.back.together02be.trade.dto.BuyRes
import com.back.together02be.trade.dto.request.TradeSellReq
import com.back.together02be.trade.dto.response.TradeSellRes
import com.back.together02be.trade.processor.TradeBuyProcessor
import com.back.together02be.trade.processor.TradeSellProcessor
import tools.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

@Service
class TradeService(
    private val tradeBuyProcessor: TradeBuyProcessor,
    private val idempotencyService: IdempotencyService,
    private val tradeSellProcessor: TradeSellProcessor,
    private val objectMapper: ObjectMapper,
) {
    fun buy(userId: Long, idempotencyKey: String, request: BuyReq): BuyRes {
        if (!idempotencyService.registerIfAbsent(idempotencyKey, userId)) {
            return idempotencyService.getStoredResponse(idempotencyKey)
                .map { objectMapper.readValue(it, BuyRes::class.java) }
                .orElseThrow { DuplicateRequestException("요청이 처리 중입니다. 잠시 후 다시 시도해주세요.") }
        }
        return try {
            tradeBuyProcessor.processBuy(userId, idempotencyKey, request)
        } catch (e: Exception) {
            idempotencyService.remove(idempotencyKey)
            throw e
        }
    }

    fun sell(userId: Long, idempotencyKey: String, req: TradeSellReq): TradeSellRes {
        if (!idempotencyService.registerIfAbsent(idempotencyKey, userId)) {
            throw IllegalStateException("이미 처리된 요청입니다.")
        }
        return try {
            tradeSellProcessor.processSell(userId, req)
        } catch (e: Exception) {
            idempotencyService.remove(idempotencyKey)
            throw e
        }
    }
}
