package com.back.together02be.asset.service;

import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserStockSseService {
    private final Map<String, List<SseEmitter>> emittersMap = new ConcurrentHashMap<>();

    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L); // 10분

        // 생명주기 콜백 등록
        emitter.onCompletion(() -> {
            log.info("SSE 연결 종료 (onCompletion)");
            removeEmitterGlobally(emitter);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE 연결 타임아웃 (onTimeout)");
            emitter.complete();
            removeEmitterGlobally(emitter);
        });
        emitter.onError((e) -> {
            log.error("SSE 연결 에러 (onError): {}", e.getMessage());
            removeEmitterGlobally(emitter);
        });

        return emitter;
    }

    // 콜백 호출 시 모든 종목에서 해당 Emitter 일괄 제거
    private void removeEmitterGlobally(SseEmitter targetEmitter) {
        emittersMap.forEach((stockCode, emitters) -> {
            emitters.remove(targetEmitter);
        });
    }

    public void addEmitter(String stockCode, SseEmitter emitter) {
        emittersMap.computeIfAbsent(stockCode, k -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    public void removeEmitter(String stockCode, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersMap.get(stockCode);
        if (emitters != null) emitters.remove(emitter);
    }

    // 캐시에서 현재가를 가져오기 위해 주입
    private final RealTimeStockPriceStore priceStore;



    // 💡 핵심 로직: 1.5초마다 현재 구독 중인 종목들의 시세만 꺼내서 구독자들에게 전송
    @Scheduled(fixedRate = 1500)
    public void broadcastOwnedStocks() {
        if (emittersMap.isEmpty()) return;

        // 현재 누군가 화면에서 보고 있는(구독 중인) 종목 코드들만 순회
        emittersMap.forEach((stockCode, emitters) -> {
            if (emitters.isEmpty()) return;

            // 저장소에서 해당 종목의 최신 가격 조회
            RealtimeStockPrice currentPrice = priceStore.get(stockCode);

            if (currentPrice != null) {

                // 해당 종목을 보유한(구독 중인) 모든 유저에게 한 번에 전송
                emitters.forEach(emitter -> {
                    try {
                        emitter.send(SseEmitter.event().name("priceUpdate").data(currentPrice));
                    } catch (Exception e) {
                        removeEmitter(stockCode, emitter);
                    }
                });
            }
        });
    }
}
