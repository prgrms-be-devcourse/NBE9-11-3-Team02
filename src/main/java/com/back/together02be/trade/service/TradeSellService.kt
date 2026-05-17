package com.back.together02be.trade.service

import com.back.together02be.global.idempotency.IdempotencyService
import com.back.together02be.trade.dto.request.TradeSellReq
import com.back.together02be.trade.dto.response.TradeSellRes
import com.back.together02be.trade.processor.TradeSellProcessor
import org.springframework.stereotype.Service

@Service
class TradeSellService(
    private val idempotencyService: IdempotencyService,
    private val tradeSellProcessor: TradeSellProcessor
) {

    /**
     * TR-02 매도
     * 멱등성 키 검증 후 매도 프로세스 진행
     */
    fun sell(userId: Long, idempotencyKey: String, req: TradeSellReq): TradeSellRes {
        // 멱등성 키 등록 시도 (이미 존재하면 예외 발생)
        if (!idempotencyService.registerIfAbsent(idempotencyKey, userId)) {
            throw IllegalStateException("이미 처리된 요청입니다.")
        }

        return try {
            tradeSellProcessor.processSell(userId, req)
        } catch (e: Exception) {
            // 실패 시 멱등성 키 삭제 후 예외 재발전
            idempotencyService.remove(idempotencyKey)
            throw e
        }
    }
}