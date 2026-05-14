package com.back.together02be.trade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.back.together02be.global.exception.DuplicateRequestException;
import com.back.together02be.global.idempotency.IdempotencyService;
import com.back.together02be.trade.dto.BuyReq;
import com.back.together02be.trade.dto.BuyRes;
import com.back.together02be.trade.processor.TradeBuyProcessor;
import com.back.together02be.trade.processor.TradeSellProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradeServiceTest {

    @Mock TradeBuyProcessor tradeBuyProcessor;
    @Mock IdempotencyService idempotencyService;
    @Mock TradeSellProcessor tradeSellProcessor;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    TradeService tradeService;

    @Test
    @DisplayName("1000명 다른 유저 동시 매수 — 모두 성공, 오류 없음")
    void 천명_다른_유저_동시_매수() throws InterruptedException {
        int userCount = 1000;
        BuyReq request = new BuyReq(1L, 10L, 70_000L);
        BuyRes mockResponse = new BuyRes(1L, "삼성전자", 10L, 70_000L, 700_000L, 49_300_000L);

        when(idempotencyService.registerIfAbsent(anyString(), anyLong())).thenReturn(true);
        when(tradeBuyProcessor.processBuy(anyLong(), anyString(), any())).thenReturn(mockResponse);

        ExecutorService executor = Executors.newFixedThreadPool(userCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (long userId = 1; userId <= userCount; userId++) {
            final long id = userId;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    tradeService.buy(id, UUID.randomUUID().toString(), request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long start = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(userCount);
        assertThat(failCount.get()).isEqualTo(0);
        verify(tradeBuyProcessor, times(userCount)).processBuy(anyLong(), anyString(), any());

        System.out.printf("[결과] 1000명 처리 완료: %dms%n", elapsed);
    }

    @Test
    @DisplayName("같은 멱등성 키 — 처리 중일 때 409 반환")
    void 같은_키_처리중_409() {
        String idempotencyKey = UUID.randomUUID().toString();
        BuyReq request = new BuyReq(1L, 10L, 70_000L);
        BuyRes mockResponse = new BuyRes(1L, "삼성전자", 10L, 70_000L, 700_000L, 49_300_000L);

        when(idempotencyService.registerIfAbsent(idempotencyKey, 1L))
                .thenReturn(true)
                .thenReturn(false);
        when(tradeBuyProcessor.processBuy(anyLong(), anyString(), any())).thenReturn(mockResponse);
        // 처리 중 상태: responseJson 없음
        when(idempotencyService.getStoredResponse(idempotencyKey)).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> tradeService.buy(1L, idempotencyKey, request));

        assertThatThrownBy(() -> tradeService.buy(1L, idempotencyKey, request))
                .isInstanceOf(DuplicateRequestException.class)
                .hasMessageContaining("처리 중");
    }

    @Test
    @DisplayName("같은 멱등성 키 — 완료된 요청은 캐시된 응답 반환 (200)")
    void 같은_키_완료된_요청_캐시_응답() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        BuyReq request = new BuyReq(1L, 10L, 70_000L);
        BuyRes mockResponse = new BuyRes(1L, "삼성전자", 10L, 70_000L, 700_000L, 49_300_000L);
        String cachedJson = "{\"tradeId\":1}";

        when(idempotencyService.registerIfAbsent(idempotencyKey, 1L)).thenReturn(false);
        when(idempotencyService.getStoredResponse(idempotencyKey)).thenReturn(Optional.of(cachedJson));
        when(objectMapper.readValue(cachedJson, BuyRes.class)).thenReturn(mockResponse);

        BuyRes result = tradeService.buy(1L, idempotencyKey, request);
        assertThat(result).isEqualTo(mockResponse);
    }

    @Test
    @DisplayName("처리 실패 시 멱등성 키 반납 — 동일 키로 재시도 허용")
    void 실패_시_키_반납_재시도_가능() {
        String idempotencyKey = UUID.randomUUID().toString();
        BuyReq request = new BuyReq(1L, 10L, 70_000L);
        BuyRes mockResponse = new BuyRes(1L, "삼성전자", 10L, 70_000L, 700_000L, 49_300_000L);

        when(idempotencyService.registerIfAbsent(idempotencyKey, 1L)).thenReturn(true);
        when(tradeBuyProcessor.processBuy(anyLong(), anyString(), any()))
                .thenThrow(new RuntimeException("일시적 오류"))
                .thenReturn(mockResponse);

        assertThatThrownBy(() -> tradeService.buy(1L, idempotencyKey, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("일시적 오류");

        verify(idempotencyService).remove(idempotencyKey);

        BuyRes result = tradeService.buy(1L, idempotencyKey, request);
        assertThat(result).isNotNull();
    }
}
