package com.back.together02be.infra.kis.websocket

import com.back.together02be.infra.kis.config.KisProperties
import com.back.together02be.infra.kis.event.WebSocketReconnectedEvent
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow

private val log = LoggerFactory.getLogger(KisWebSocketClient::class.java)

@Component
class KisWebSocketClient(
    private val kisProperties: KisProperties,
    private val approvalKeyService: ApprovalKeyService,
    private val handler: KisWebSocketHandler,
    private val eventPublisher: ApplicationEventPublisher
) {
    private enum class State { CONNECTING, CONNECTED, SHUTTING_DOWN }

    companion object {
        private const val INITIAL_DELAY_MS = 1_000L
        private const val MAX_DELAY_MS = 30_000L
        private const val BACKOFF_MULTIPLIER = 2.0
        private const val JITTER_FACTOR = 0.2
    }

    private lateinit var client: WebSocketClient
    private val state = AtomicReference(State.CONNECTING)
    private val reconnectAttempt = AtomicInteger(0)
    private lateinit var reconnectScheduler: ScheduledExecutorService

    @PostConstruct
    fun start() {  // connect() → start()로 분리
        reconnectScheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "kis-reconnect").also { it.isDaemon = true }
        }
        connect()
    }

    private fun connect() {
        if (state.get() == State.SHUTTING_DOWN) return

        try {
            val approvalKey = approvalKeyService.getApprovalKey()
            handler.setApprovalKey(approvalKey)

            client = object : WebSocketClient(URI(kisProperties.wsUrl)) {
                override fun onOpen(handshake: ServerHandshake) {
                    log.info("한국투자 증권 WebSocket 연결 성공")
                    val wasReconnect = reconnectAttempt.get() > 0
                    state.set(State.CONNECTED)
                    reconnectAttempt.set(0)
                    handler.onOpen(this)
                    handler.resubscribeAll() // 첫 연결 시 no-op, 재연결 시 복원

                    if (wasReconnect) {
                        eventPublisher.publishEvent(WebSocketReconnectedEvent())
                    }
                }

                override fun onMessage(message: String) = handler.onMessage(message)

                override fun onClose(code: Int, reason: String, remote: Boolean) {
                    log.warn("WebSocket 연결 종료 - code: {}, reason: {}", code, reason)
                    scheduleReconnect()
                }

                override fun onError(e: Exception) {
                    log.error("WebSocket 오류", e)
                    scheduleReconnect()
                }
            }

            val connected = client.connectBlocking()
            if (!connected) {
                log.warn("WebSocket connectBlocking 실패 (시도 #{})", reconnectAttempt.get())
                scheduleNextAttempt()
            }
        } catch (e: Exception) {
            log.error("WebSocket 연결 실패 (시도 #{})", reconnectAttempt.get(), e)
            scheduleNextAttempt()
        }
    }

    @PreDestroy
    fun stop() {
        state.set(State.SHUTTING_DOWN)

        if (::client.isInitialized && client.isOpen) {
            client.close()
        }

        reconnectScheduler.shutdown()
        try {
            if (!reconnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                reconnectScheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            reconnectScheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }

    private fun scheduleReconnect() {
        if (!state.compareAndSet(State.CONNECTED, State.CONNECTING)) return
        scheduleNextAttempt()
    }

    private fun scheduleNextAttempt() {
        if (state.get() == State.SHUTTING_DOWN) return

        val attempt = reconnectAttempt.incrementAndGet()
        val delay = calculateBackoff(attempt)
        log.info("재연결 예약 - 시도 #{}, {}ms 후", attempt, delay)
        reconnectScheduler.schedule(::connect, delay, TimeUnit.MILLISECONDS)
    }

    private fun calculateBackoff(attempt: Int): Long {
        val base = minOf(MAX_DELAY_MS.toDouble(),
            INITIAL_DELAY_MS.toDouble() * BACKOFF_MULTIPLIER.pow(attempt - 1))
        val jitter = 1 + (Math.random() * 2 - 1) * JITTER_FACTOR
        return (base * jitter).toLong()
    }

    fun subscribe(stockCode: String) = handler.subscribe(stockCode)

    fun unsubscribe(stockCode: String) = handler.unsubscribe(stockCode)
}
