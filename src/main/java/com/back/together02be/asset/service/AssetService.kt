package com.back.together02be.asset.service

import com.back.together02be.asset.dto.response.StockInfoRes
import com.back.together02be.asset.dto.response.UserStockRes
import com.back.together02be.asset.entity.UserStock
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.asset.repository.UserStockRepository
import com.back.together02be.stock.service.RealTimeStockPriceStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

@Service
class AssetService(
    private val userAccountRepository: UserAccountRepository,
    private val userStockRepository: UserStockRepository,
    private val realTimeStockPriceStore: RealTimeStockPriceStore,
    private val userStockSseService: UserStockSseService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 예수금 조회 메서드
    fun getDeposit(userId: Long): Long {
        val userAccount = userAccountRepository.findByUsersId(userId)
            .orElseThrow { IllegalArgumentException("계좌 없음") }
        return userAccount.deposit
    }

    // 보유 종목 조회 메서드
    fun getUserStocks(userId: Long): List<UserStockRes> {
        val userStocks = userStockRepository.findAllByUsersId(userId)
        return getUserStocksRealtimePrice(userStocks)
    }

    // 유저 보유 종목 현재 시세 조회
    fun getUserStocksRealtimePrice(userStocks: List<UserStock>): List<UserStockRes> {
        return userStocks.map { userStock ->
            val stockCode = userStock.stock.stockCode

            // Safe Call(?.)과 Elvis Operator(?:), 그리고 안전한 형변환(toLongOrNull) 결합
            val currentPrice = realTimeStockPriceStore.get(stockCode)
                ?.price
                ?.toLongOrNull() ?: 0L

            UserStockRes.from(userStock, currentPrice)
        }
    }

    // 총 매수 금액 조회 메서드
    fun getTotalAmountByUserId(userId: Long): Long {
        return userAccountRepository.findByUsersId(userId)
            .orElseThrow { RuntimeException("계좌 없음") }
            .totalPurchase
    }

    // 보유 주식 정보(종목코드, 수량) 조회
    fun getStockInfo(userId: Long): List<StockInfoRes> {
        return userStockRepository.findAllByUsersId(userId)
            .map { StockInfoRes(it.stock.stockCode, it.quantity) } // 암시적 변수 it 사용
    }

    // SSE 다중 종목 구독
    fun subscribeToUserStocks(userId: Long): SseEmitter {
        val userStocks = userStockRepository.findAllByUsersId(userId)
        val stockCodes = userStocks.map { it.stock.stockCode }

        val emitter = userStockSseService.createEmitter()

        // 💡 만약 보유 주식이 없다면, 503 에러를 막기 위해 연결 더미 데이터만 보내고 유지합니다.
        if (stockCodes.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("CONNECT").data("no_stocks"))
            } catch (e: IOException) {
                emitter.completeWithError(e)
            }
            return emitter
        }

        stockCodes.forEach { code -> userStockSseService.addEmitter(code, emitter) }

        // 코틀린에서 Runnable 인터페이스 람다 구현
        val onCompletion = Runnable {
            stockCodes.forEach { code -> userStockSseService.removeEmitter(code, emitter) }
        }

        emitter.onCompletion(onCompletion)
        emitter.onTimeout(onCompletion)
        emitter.onError { onCompletion.run() }

        // 💡 503 에러 방지용 첫 이벤트 전송
        try {
            emitter.send(SseEmitter.event().name("CONNECT").data("connected"))
        } catch (e: IOException) {
            emitter.completeWithError(e)
        }

        return emitter
    }
}