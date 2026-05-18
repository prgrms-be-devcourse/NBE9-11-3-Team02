package com.back.together02be.trade.service

import com.back.together02be.global.exception.DuplicateRequestException
import com.back.together02be.global.idempotency.IdempotencyService
import com.back.together02be.trade.dto.BuyReq
import com.back.together02be.trade.dto.BuyRes
import com.back.together02be.trade.processor.TradeBuyProcessor
import com.back.together02be.trade.processor.TradeSellProcessor
import tools.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Suppress("UNCHECKED_CAST")
private fun <T> anyArg(): T = Mockito.any<T>() as T

@ExtendWith(MockitoExtension::class)
class TradeServiceTest {

    @Mock lateinit var tradeBuyProcessor: TradeBuyProcessor
    @Mock lateinit var idempotencyService: IdempotencyService
    @Mock lateinit var tradeSellProcessor: TradeSellProcessor
    @Mock lateinit var objectMapper: ObjectMapper

    @InjectMocks
    lateinit var tradeService: TradeService

    @Test
    @DisplayName("1000명 다른 유저 동시 매수 — 모두 성공, 오류 없음")
    fun 천명_다른_유저_동시_매수() {
        val userCount = 1000
        val request = BuyReq(1L, 10L, 70_000L)
        val mockResponse = BuyRes(1L, "삼성전자", 10L, 70_000L, 700_000L, 49_300_000L)

        Mockito.`when`(idempotencyService.registerIfAbsent(anyString(), anyLong())).thenReturn(true)
        Mockito.`when`(tradeBuyProcessor.processBuy(anyLong(), anyString(), anyArg())).thenReturn(mockResponse)

        val executor = Executors.newFixedThreadPool(userCount)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(userCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        for (userId in 1L..userCount) {
            executor.submit {
                try {
                    startLatch.await()
                    tradeService.buy(userId, UUID.randomUUID().toString(), request)
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        val start = System.currentTimeMillis()
        startLatch.countDown()
        doneLatch.await(10, TimeUnit.SECONDS)
        val elapsed = System.currentTimeMillis() - start
        executor.shutdown()

        assertThat(successCount.get()).isEqualTo(userCount)
        assertThat(failCount.get()).isEqualTo(0)
        Mockito.verify(tradeBuyProcessor, Mockito.times(userCount)).processBuy(anyLong(), anyString(), anyArg())

        println("[결과] 1000명 처리 완료: ${elapsed}ms")
    }

    @Test
    @DisplayName("같은 멱등성 키 — 처리 중일 때 409 반환")
    fun 같은_키_처리중_409() {
        val idempotencyKey = UUID.randomUUID().toString()
        val request = BuyReq(1L, 10L, 70_000L)
        val mockResponse = BuyRes(1L, "삼성전자", 10L, 70_000L, 700_000L, 49_300_000L)

        Mockito.`when`(idempotencyService.registerIfAbsent(idempotencyKey, 1L))
            .thenReturn(true)
            .thenReturn(false)
        Mockito.`when`(tradeBuyProcessor.processBuy(anyLong(), anyString(), anyArg())).thenReturn(mockResponse)
        Mockito.`when`(idempotencyService.getStoredResponse(idempotencyKey)).thenReturn(Optional.empty())

        assertThatNoException().isThrownBy { tradeService.buy(1L, idempotencyKey, request) }

        assertThatThrownBy { tradeService.buy(1L, idempotencyKey, request) }
            .isInstanceOf(DuplicateRequestException::class.java)
            .hasMessageContaining("처리 중")
    }

    @Test
    @DisplayName("같은 멱등성 키 — 완료된 요청은 캐시된 응답 반환 (200)")
    fun 같은_키_완료된_요청_캐시_응답() {
        val idempotencyKey = UUID.randomUUID().toString()
        val request = BuyReq(1L, 10L, 70_000L)
        val mockResponse = BuyRes(1L, "삼성전자", 10L, 70_000L, 700_000L, 49_300_000L)
        val cachedJson = "{\"tradeId\":1}"

        Mockito.`when`(idempotencyService.registerIfAbsent(idempotencyKey, 1L)).thenReturn(false)
        Mockito.`when`(idempotencyService.getStoredResponse(idempotencyKey)).thenReturn(Optional.of(cachedJson))
        Mockito.`when`(objectMapper.readValue(cachedJson, BuyRes::class.java)).thenReturn(mockResponse)

        val result = tradeService.buy(1L, idempotencyKey, request)
        assertThat(result).isEqualTo(mockResponse)
    }

    @Test
    @DisplayName("처리 실패 시 멱등성 키 반납 — 동일 키로 재시도 허용")
    fun 실패_시_키_반납_재시도_가능() {
        val idempotencyKey = UUID.randomUUID().toString()
        val request = BuyReq(1L, 10L, 70_000L)
        val mockResponse = BuyRes(1L, "삼성전자", 10L, 70_000L, 700_000L, 49_300_000L)

        Mockito.`when`(idempotencyService.registerIfAbsent(idempotencyKey, 1L)).thenReturn(true)
        Mockito.`when`(tradeBuyProcessor.processBuy(anyLong(), anyString(), anyArg()))
            .thenThrow(RuntimeException("일시적 오류"))
            .thenReturn(mockResponse)

        assertThatThrownBy { tradeService.buy(1L, idempotencyKey, request) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessage("일시적 오류")

        Mockito.verify(idempotencyService).remove(idempotencyKey)

        val result = tradeService.buy(1L, idempotencyKey, request)
        assertThat(result).isNotNull()
    }
}