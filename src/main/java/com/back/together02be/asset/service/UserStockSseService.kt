package com.back.together02be.asset.service

import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.service.RealTimeStockPriceStore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class UserStockSseService(
    private val priceStore: RealTimeStockPriceStore // 생성자 주입
) {
    // Lombok의 @Slf4j를 대체하는 표준 로거 선언
    private val log = LoggerFactory.getLogger(UserStockSseService::class.java)

    private val emittersMap = ConcurrentHashMap<String, MutableList<SseEmitter>>()

    fun createEmitter(): SseEmitter {
        return SseEmitter(10 * 60 * 1000L)
    }

    fun addEmitter(stockCode: String, emitter: SseEmitter) {
        emittersMap.computeIfAbsent(stockCode) { CopyOnWriteArrayList() }.add(emitter)
    }

    fun removeEmitter(stockCode: String, emitter: SseEmitter) {
        // Kotlin의 Null 안전 호출 연산자(?.)를 사용하여 간결하게 처리
        emittersMap[stockCode]?.remove(emitter)
    }

    // 💡 핵심 로직: 1.5초마다 현재 구독 중인 종목들의 시세만 꺼내서 구독자들에게 전송
    @Scheduled(fixedRate = 1500)
    fun broadcastOwnedStocks() {
        if (emittersMap.isEmpty()) return

        // 현재 누군가 화면에서 보고 있는(구독 중인) 종목 코드들만 순회
        emittersMap.forEach { (stockCode, emitters) ->
            if (emitters.isEmpty()) return@forEach // Kotlin forEach 람다 내에서는 continue 대신 return@레이블 사용

            priceStore.get(stockCode)?.let { currentPrice ->
                emitters.forEach { emitter ->
                    try {
                        emitter.send(SseEmitter.event().name("priceUpdate").data(currentPrice))
                    } catch (e: Exception) {
                        log.warn("SSE 전송 실패. Emitter를 제거합니다. 종목코드: {}, 에러: {}", stockCode, e.message)
                        removeEmitter(stockCode, emitter)
                    }
                }
            }
        }
    }
}