package com.back.together02be.infra.kis

import com.back.together02be.infra.kis.rest.KisPriceClient
import com.back.together02be.infra.kis.websocket.KisWebSocketClient
import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.entity.Stock
import com.back.together02be.stock.repository.StockRepository
import com.back.together02be.stock.service.RealTimeStockPriceStore
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

private val log = LoggerFactory.getLogger(StockSubscriptionInitializer::class.java)

@Component
class StockSubscriptionInitializer(
    private val stockRepository: StockRepository,
    private val kisPriceClient: KisPriceClient,
    private val kisWebSocketClient: KisWebSocketClient,
    private val rtStockPriceStore: RealTimeStockPriceStore
) {
    @EventListener(ApplicationReadyEvent::class)
    @Async
    fun initialize() {
        log.info("주식 종목 구독 초기화 시작")

        val stocks = stockRepository.findAll()
        if (stocks.isEmpty()) {
            log.warn("등록된 주식 종목 없음, 초기화 중단")
            return
        }

        seedPricesByRest(stocks)

        // stocks.forEach{stock -> {
        //     val cached = rtStockPriceStore.get(stock.stockCode)
        //     log.info("rest 캐시 확인 - {}: currentPrice={}, changeRate={}",
        //         stock.stockCode,
        //         cached?.price ?: "null",
        //         cached?.changeRate ?: "null")
        // }

        subscribeAll(stocks)

        log.info(
            "주식 종목 구독 초기화 완료 ({}개 종목)",
            stocks.size
        )
    }

    private fun seedPricesByRest(stocks: List<Stock>) {
        val token = kisPriceClient.accessToken
        var success = 0
        var fail = 0

        for (stock in stocks) {
            try {
                val restStock = kisPriceClient.getCurrentPrice(token, stock.stockCode)

                // 웹소켓이 채운 값은 덮어쓰지 않음
                rtStockPriceStore.putIfAbsent(
                    stock.stockCode,
                    RealtimeStockPrice.fromRest(stock.stockCode, restStock.output)
                )
                success++
            } catch (e: Exception) {
                fail++
                log.warn(
                    "REST 주식 종목 구독 실패: {} - {} - {}",
                    stock.stockCode,
                    stock.stockName,
                    e.message
                )
            }

            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn("주식 종목 시딩 중단됨")
                return
            }
        }
        log.info("REST 주식 종목 시딩 결과: 성공 {}, 실패 {}", success, fail)
    }

    private fun subscribeAll(stocks: List<Stock>) {
        for (stock in stocks) {
            try {
                kisWebSocketClient.subscribe(stock.stockCode)
            } catch (e: Exception) {
                log.warn(
                    "주식 종목 구독 실패: {} - {} - {}",
                    stock.stockCode,
                    stock.stockName,
                    e.message
                )
            }
        }
    }
}