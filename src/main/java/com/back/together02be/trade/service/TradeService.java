package com.back.together02be.trade.service;

import com.back.together02be.global.exception.DuplicateRequestException;
import com.back.together02be.global.idempotency.IdempotencyService;
import com.back.together02be.trade.dto.BuyReq;
import com.back.together02be.trade.dto.BuyRes;
import com.back.together02be.trade.dto.request.TradeSellReq;
import com.back.together02be.trade.dto.response.TradeSellRes;
import com.back.together02be.trade.processor.TradeBuyProcessor;
import com.back.together02be.trade.processor.TradeSellProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final TradeBuyProcessor tradeBuyProcessor;
    private final IdempotencyService idempotencyService;
    private final TradeSellProcessor tradeSellProcessor;
    private final ObjectMapper objectMapper;

    /**
     * TR-01 매수
     * 멱등성 키 — DB UNIQUE 제약으로 중복 전송 차단, 서버 재시작 후에도 DB에 키가 남아 중복 체결 방지.
     * 완료된 요청은 캐시된 응답을 그대로 반환 (HTTP 멱등성 표준).
     */
    @SneakyThrows
    public BuyRes buy(Long userId, String idempotencyKey, BuyReq request) {
        if (!idempotencyService.registerIfAbsent(idempotencyKey, userId)) {
            return idempotencyService.getStoredResponse(idempotencyKey)
                    .map(this::deserializeBuyRes)
                    .orElseThrow(() -> new DuplicateRequestException("요청이 처리 중입니다. 잠시 후 다시 시도해주세요."));
        }

        try {
            return tradeBuyProcessor.processBuy(userId, idempotencyKey, request);
        } catch (Exception e) {
            idempotencyService.remove(idempotencyKey);
            throw e;
        }
    }

    public TradeSellRes sell(Long userId, String idempotencyKey, TradeSellReq req) {
        if (!idempotencyService.registerIfAbsent(idempotencyKey, userId)) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }
        try {
            return tradeSellProcessor.processSell(userId,req);
        } catch (Exception e) {
            idempotencyService.remove(idempotencyKey);
            throw e;
        }
    }

    @SneakyThrows
    private BuyRes deserializeBuyRes(String json) {
        return objectMapper.readValue(json, BuyRes.class);
    }
}
