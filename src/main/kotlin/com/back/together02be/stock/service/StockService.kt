package com.back.together02be.stock.service

import com.back.together02be.global.extend.getOrThrow
import com.back.together02be.stock.dto.response.StockListRes
import com.back.together02be.stock.dto.response.StockPriceRes
import com.back.together02be.stock.entity.Stock
import com.back.together02be.stock.repository.StockRepository
import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val log = LoggerFactory.getLogger(StockService::class.java)

@Service
class StockService(
    private val stockRepository: StockRepository,

    // 전체 종목/상세 종목 조회에서 공통으로 사용하는 실시간 시세 캐시 저장소
    private val rtStockPriceStore: RealTimeStockPriceStore
) {

    companion object {
        private const val LIST_SSE_INTERVAL_MS: Long = 1500 //전체 종목 시세 갱신 주기
        private const val SSE_INTERVAL_MS: Long = 500 // 상세 종목 시세 갱신 주기
    }

    // 전체 종목 조회 시 DB의 종목 기본 정보와 실시간 시세 캐시를 조합해 응답을 생성한다.
    fun getStocks(): List<StockListRes> =
        stockRepository.findAll().map { stock ->
            val cached = rtStockPriceStore.get(stock.stockCode)
            StockListRes(
                stock.id,
                stock.stockCode,
                stock.stockName,
                cached?.price?.toLongSafely(),
                cached?.changeRate?.toDoubleSafely()
            )
        }

    // 문자열 가격 값을 Long으로 안전하게 변환하고, 값이 없거나 형식이 잘못되면 null을 반환한다.
    private fun String.toLongSafely(): Long? =
        takeIf { it.isNotBlank() }?.toLongOrNull()

    // 문자열 등락률 값을 Double로 안전하게 변환하고, 값이 없거나 형식이 잘못되면 null을 반환한다.
    private fun String.toDoubleSafely(): Double? =
        takeIf { it.isNotBlank() }?.toDoubleOrNull()

    // 전체 종목 실시간 정보를 1.5초 주기로 SSE 전송
    fun createStockListSseEmitter(): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        val executor = Executors.newSingleThreadScheduledExecutor()

        executor.scheduleAtFixedRate({
            try {
                emitter.send(getStocks())
            } catch (e: IOException) {
                emitter.complete()
                executor.shutdown()
            } catch (e: Exception) {
                log.error("전체 종목 SSE 오류: {}", e.message, e)
                emitter.complete()
                executor.shutdown()
            }
        }, 0, LIST_SSE_INTERVAL_MS, TimeUnit.MILLISECONDS)

        emitter.onCompletion { executor.shutdown() }
        emitter.onTimeout {
            executor.shutdown()
            log.info("전체 종목 SSE 타임아웃")
        }

        return emitter
    }

    fun createSseEmitter(stockCode: String): SseEmitter {
        findStock(stockCode)

        val emitter = SseEmitter(Long.MAX_VALUE)
        val executor = Executors.newSingleThreadScheduledExecutor()

        executor.scheduleAtFixedRate({ // (작업, 초기 지연, 주기, 단위)
            try {
                rtStockPriceStore.get(stockCode)?.let { emitter.send(it) }
            } catch (e: IOException) {
                emitter.complete()
                executor.shutdown()
            } catch (e: Exception) {
                log.error("SSE 오류 - 종목: {} | 원인: {}", stockCode, e.message, e)
                emitter.complete()
                executor.shutdown()
            }
        }, 0, SSE_INTERVAL_MS, TimeUnit.MILLISECONDS)

        emitter.onCompletion({ executor.shutdown() })

        emitter.onTimeout(Runnable {
            executor.shutdown()
            log.info("SSE 타임아웃 - 종목: {}", stockCode)
        })

        return emitter
    }

    fun getStockPrice(stockCode: String): StockPriceRes =
        StockPriceRes.from(findStock(stockCode))


    private fun findStock(stockCode: String): Stock =
        stockRepository.findByStockCode(stockCode)
            .getOrThrow { EntityNotFoundException("존재하지 않는 종목코드입니다: $stockCode") }

}
