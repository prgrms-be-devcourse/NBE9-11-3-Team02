package com.back.together02be.infra.kis.websocket

import com.back.together02be.infra.kis.config.KisProperties
import com.back.together02be.infra.kis.event.WebSocketReconnectedEvent
import com.back.together02be.infra.kis.notification.DiscordNotifier
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.pow

private val log = LoggerFactory.getLogger(KisWebSocketClient::class.java)

@Component
class KisWebSocketClient(
    private val kisProperties: KisProperties,
    private val approvalKeyService: ApprovalKeyService,
    private val handler: KisWebSocketHandler,
    private val eventPublisher: ApplicationEventPublisher,
    private val discordNotifier: DiscordNotifier
) {
    private enum class State { CONNECTING, CONNECTED, SHUTTING_DOWN }

    companion object {
        private const val INITIAL_DELAY_MS = 1_000L // 첫 재연결 대기 시간 (1초)
        private const val MAX_DELAY_MS = 30_000L // 알림 발사 전 재연결 최대 대기 시간 (30초)
        private const val BACKOFF_MULTIPLIER = 2.0 // 재시도마다 대기 시간 증가 배수
        private const val JITTER_FACTOR = 0.2 // 동시 재연결 분산을 위한 랜덤 오차 범위
        private val ALERT_THRESHOLD: Duration = Duration.ofMinutes(5) // 연결 끊긴 후 알림 전송까지 시간
        private const val ALERT_BACKOFF_MS = 2 * 60_000L // 알림 발사 후 재연결 시도 간격 상한 (2분마다 재시도)
    }

    private lateinit var client: WebSocketClient
    private val state = AtomicReference(State.CONNECTING)
    private val reconnectAttempt = AtomicInteger(0)
    private lateinit var reconnectScheduler: ScheduledExecutorService
    @Volatile private var lastConnectedAt: Instant = Instant.now()
    private val alertSent = AtomicBoolean(false)

    @PostConstruct
    fun start() {
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
                    val now = Instant.now()
                    val downtime = Duration.between(lastConnectedAt, now)

                    state.set(State.CONNECTED)
                    reconnectAttempt.set(0)
                    lastConnectedAt = now

                    handler.onOpen(this)
                    handler.resubscribeAll() // 첫 연결 시 no-op, 재연결 시 복원

                    if (wasReconnect) {
                        eventPublisher.publishEvent(WebSocketReconnectedEvent())
                    }

                    // 알림 발송 후 복구된 경우 → 복구 알림
                    if (alertSent.compareAndSet(true, false)) {
                        discordNotifier.sendAlert(
                            "KIS WebSocket 복구",
                            "다운타임: ${formatDuration(downtime)}"
                        )
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

        // 5분 이상 다운 → 장기 다운 알림 (최초 1회)
        val downtime = Duration.between(lastConnectedAt, Instant.now())
        if (downtime >= ALERT_THRESHOLD && alertSent.compareAndSet(false, true)) {
            discordNotifier.sendAlert(
                "KIS WebSocket 장기 다운",
                "다운타임: ${formatDuration(downtime)}\n재시도 횟수: $attempt\n자동 재시도는 계속됩니다."
            )
        }
    }

    private fun formatDuration(d: Duration): String {
        val minutes = d.toMinutes()
        val seconds = d.minusMinutes(minutes).seconds
        return "${minutes}분 ${seconds}초"
    }

    private fun calculateBackoff(attempt: Int): Long {
        val maxDelay = if (alertSent.get()) ALERT_BACKOFF_MS else MAX_DELAY_MS
        val base = minOf(maxDelay.toDouble(),
            INITIAL_DELAY_MS.toDouble() * BACKOFF_MULTIPLIER.pow(attempt - 1))
        val jitter = 1 + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * JITTER_FACTOR
        return (base * jitter).toLong()
    }

    fun subscribe(stockCode: String) = handler.subscribe(stockCode)

    fun unsubscribe(stockCode: String) = handler.unsubscribe(stockCode)
}
