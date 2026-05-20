package com.back.together02be.infra.kis.websocket

import com.back.together02be.infra.kis.config.KisProperties
import com.back.together02be.infra.kis.event.WebSocketReconnectedEvent
import com.back.together02be.infra.kis.notification.DiscordNotifier
import org.assertj.core.api.Assertions.assertThat
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.junit.jupiter.api.*
import org.mockito.ArgumentMatchers.contains
import org.mockito.kotlin.*
import org.springframework.context.ApplicationEventPublisher
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * KisWebSocketClient 단위 테스트
 *
 * 다루는 흐름:
 *   - 초기 연결: ApprovalKey 발급, handler 콜백, 메시지 forwarding
 *   - 재연결: 끊김 감지 후 백오프, 재구독, 이벤트 발행
 *   - 종료: graceful shutdown 시 재연결 시도 중단
 *   - 위임: subscribe/unsubscribe의 handler로의 위임
 *   - 장기 다운타임 알림: Discord 알림 발사/복구 검증
 *
 * 전략: @SpringBootTest 없이 객체 수동 생성.
 *       실제 WebSocket 연결은 로컬 TestWebSocketServer로 대체.
 */
internal class KisWebSocketClientTest {

    // 가짜 서버 & SUT
    private lateinit var fakeServer: TestWebSocketServer
    private lateinit var sut: KisWebSocketClient

    // Mock/Stub
    private lateinit var kisProperties: KisProperties
    private lateinit var approvalKeyService: ApprovalKeyService
    private lateinit var handler: KisWebSocketHandler
    private lateinit var eventPublisher: ApplicationEventPublisher
    private lateinit var discordNotifier: DiscordNotifier

    // Lifecycle
    @BeforeEach
    fun setUp() {
        // 1) 가짜 서버 기동 (OS가 랜덤 포트 할당)
        fakeServer = TestWebSocketServer()
        fakeServer.start()
        fakeServer.waitUntilStarted() // 서버가 실제로 준비될 때까지 대기

        val port = fakeServer.port // OS가 배정한 랜덤 포트 번호 획득

        // 2) KisProperties — 가짜 서버 URL을 반환하도록 stub
        kisProperties = mock()
        whenever(kisProperties.wsUrl).thenReturn("ws://127.0.0.1:$port")

        // 3) ApprovalKeyService — 고정 키 반환
        approvalKeyService = mock()
        whenever(approvalKeyService.getApprovalKey()).thenReturn("test-approval-key")

        // 4) KisWebSocketHandler — 호출 여부 검증용 mock
        handler = mock()
        eventPublisher = mock()
        discordNotifier = mock()

        // 5) SUT 수동 생성 (@PostConstruct인 connect()는 각 테스트에서 직접 호출)
        sut = KisWebSocketClient(kisProperties, approvalKeyService, handler, eventPublisher, discordNotifier)
    }

    @AfterEach
    fun tearDown() {
        // client 필드가 null일 수 있으므로 방어적으로 처리
        try {
            sut.stop()
        } catch (ignored: Exception) {
        }
        fakeServer.stop(1000)
    }

    // 테스트 케이스
    @Nested
    @DisplayName("초기 연결")
    inner class ConnectionTest {
        @Test
        @DisplayName("start() 호출 시 ApprovalKey를 발급받아 handler.setApprovalKey()에 전달한다")
        fun start_setsApprovalKeyOnHandler() {
            sut.start()

            verify(approvalKeyService).getApprovalKey()
            verify(handler).setApprovalKey("test-approval-key")
        }

        @Test
        @DisplayName("연결 수립 후 handler.onOpen(client)이 WebSocketClient 인스턴스와 함께 호출된다")
        @Throws(Exception::class)
        fun connect_callsHandlerOnOpenWithWebSocketClientInstance() {
            val onOpenLatch = CountDownLatch(1)

            // latch를 푸는 조건 설정
            doAnswer { onOpenLatch.countDown(); null } // 비동기이기 때문에 latch 설정(latch = 빗장)
                .whenever(handler).onOpen(any())

            sut.start()

            val triggered = onOpenLatch.await(3, TimeUnit.SECONDS)
            assertThat(triggered).`as`("3초 내에 handler.onOpen()이 호출되어야 한다").isTrue()
            verify(handler).onOpen(any<WebSocketClient>())
        }

        @Test
        @DisplayName("서버가 메시지를 전송하면 handler.onMessage()가 동일 문자열로 호출된다")
        @Throws(Exception::class)
        fun serverMessage_isForwardedToHandlerOnMessage() {
            val onOpenLatch = CountDownLatch(1)
            val onMessageLatch = CountDownLatch(1)

            doAnswer { onOpenLatch.countDown(); null }.whenever(handler).onOpen(any())
            doAnswer { onMessageLatch.countDown(); null }.whenever(handler).onMessage(any())

            sut.start()
            val opened = onOpenLatch.await(3, TimeUnit.SECONDS) // 연결 수립 후에 메시지를 받아야하므로 먼저 기다림
            assertThat(opened).`as`("메시지 전송 전에 연결이 수립되어야 한다").isTrue()

            fakeServer.broadcast("test-message-payload") // 가짜 서버가 메시지 전송

            val received = onMessageLatch.await(3, TimeUnit.SECONDS)
            assertThat(received).`as`("3초 내에 handler.onMessage()가 호출되어야 한다").isTrue()
            verify(handler).onMessage("test-message-payload")
        }
    }

    @Nested
    @DisplayName("재연결 동작")
    inner class ReconnectionTest {

        @Test
        @DisplayName("첫 연결에서는 WebSocketReconnectedEvent가 발행되지 않는다")
        fun firstConnect_doesNotPublishReconnectedEvent() {
            val onOpenLatch = CountDownLatch(1)
            doAnswer { onOpenLatch.countDown(); null }.whenever(handler).onOpen(any())

            sut.start()
            onOpenLatch.await(3, TimeUnit.SECONDS)

            verify(eventPublisher, never()).publishEvent(any<WebSocketReconnectedEvent>())
        }

        @Test
        @DisplayName("서버 끊김 후 재연결 시도가 예약된다")
        fun serverClose_schedulesReconnect() {
            val onOpenLatch = CountDownLatch(1)
            doAnswer { onOpenLatch.countDown(); null }.whenever(handler).onOpen(any())

            sut.start()
            onOpenLatch.await(3, TimeUnit.SECONDS)

            fakeServer.stop(500)
            Thread.sleep(2000) // 백오프 1초 + 여유

            assertThat(extractReconnectAttempt())
                .`as`("재연결 시도 카운터가 증가해야 한다")
                .isGreaterThan(0)
        }

        @Test
        @DisplayName("재연결 성공 시 handler.resubscribeAll()이 호출되고 이벤트가 발행된다")
        fun reconnect_callsResubscribeAndPublishesEvent() {
            val firstOpen = CountDownLatch(1)
            val secondOpen = CountDownLatch(1)
            val openCount = AtomicInteger(0)

            doAnswer {
                val n = openCount.incrementAndGet()
                if (n == 1) firstOpen.countDown()
                if (n >= 2) secondOpen.countDown()
                null
            }.whenever(handler).onOpen(any())

            val port = fakeServer.port
            sut.start()
            firstOpen.await(3, TimeUnit.SECONDS)

            fakeServer.stop(500)

            // 같은 포트로 새 서버 띄움
            fakeServer = TestWebSocketServer(port)
            fakeServer.start()
            fakeServer.waitUntilStarted()

            val reconnected = secondOpen.await(5, TimeUnit.SECONDS)
            assertThat(reconnected).`as`("5초 내 재연결되어야 한다").isTrue()

            verify(handler, atLeast(2)).onOpen(any())
            verify(handler, atLeast(1)).resubscribeAll()
            verify(eventPublisher, atLeast(1)).publishEvent(any<WebSocketReconnectedEvent>())
        }
    }

    @Nested
    @DisplayName("종료 동작")
    inner class ShutdownTest {

        @Test
        @DisplayName("stop() 후에는 서버가 끊겨도 재연결 시도하지 않는다")
        fun afterStop_doesNotReconnect() {
            val onOpenLatch = CountDownLatch(1)
            doAnswer { onOpenLatch.countDown(); null }.whenever(handler).onOpen(any())

            sut.start()
            onOpenLatch.await(3, TimeUnit.SECONDS)

            sut.stop()
            fakeServer.stop(500)
            Thread.sleep(2000)

            verify(handler, times(1)).onOpen(any())
            assertThat(extractReconnectAttempt())
                .`as`("stop() 이후 재연결 시도가 없어야 한다")
                .isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("위임 동작")
    inner class DelegationTest {
        @Test
        @DisplayName("subscribe() 호출 시 handler.subscribe()로 그대로 위임된다")
        @Throws(Exception::class)
        fun subscribe_delegatesToHandler() {
            // subscribe/unsubscribe는 handler로의 단순 위임이므로
            // onOpen 대기 없이 connect() 후 바로 검증
            sut.start()
            sut.subscribe("005930")
            verify(handler).subscribe("005930")
        }

        @Test
        @DisplayName("unsubscribe() 호출 시 handler.unsubscribe()로 그대로 위임된다")
        @Throws(Exception::class)
        fun unsubscribe_delegatesToHandler() {
            sut.start()
            sut.unsubscribe("005930")
            verify(handler).unsubscribe("005930")
        }

    }

    @Nested
    @DisplayName("장기 다운타임 알림")
    inner class AlertTest {

        @Test
        @DisplayName("5분 미만 끊김에서는 다운 알림이 발사되지 않는다")
        fun shortDowntime_doesNotSendAlert() {
            val onOpenLatch = CountDownLatch(1)
            doAnswer { onOpenLatch.countDown(); null }.whenever(handler).onOpen(any())

            sut.start()
            onOpenLatch.await(3, TimeUnit.SECONDS)

            // lastConnectedAt이 방금이므로 다운타임 < 5분
            fakeServer.stop(500)
            Thread.sleep(2000)

            verify(discordNotifier, never()).sendAlert(contains("장기 다운"), any())
        }

        @Test
        @DisplayName("5분 이상 끊김 시 다운 알림이 1회 발사된다")
        fun longDowntime_sendsAlertOnce() {
            val onOpenLatch = CountDownLatch(1)
            doAnswer { onOpenLatch.countDown(); null }.whenever(handler).onOpen(any())

            sut.start()
            onOpenLatch.await(3, TimeUnit.SECONDS)

            // 6분 전 연결된 것처럼 조작 → 임계값 즉시 초과
            setLastConnectedAt(Instant.now().minus(Duration.ofMinutes(6)))

            fakeServer.stop(500)
            Thread.sleep(2000)

            verify(discordNotifier, times(1)).sendAlert(contains("장기 다운"), any())
        }

        @Test
        @DisplayName("이미 알림이 발사된 상태에서는 중복 발사되지 않는다")
        fun afterAlertSent_doesNotSendAgain() {
            val onOpenLatch = CountDownLatch(1)
            doAnswer { onOpenLatch.countDown(); null }.whenever(handler).onOpen(any())

            sut.start()
            onOpenLatch.await(3, TimeUnit.SECONDS)

            setAlertSent(true)
            setLastConnectedAt(Instant.now().minus(Duration.ofMinutes(6)))

            fakeServer.stop(500)
            Thread.sleep(2000)

            verify(discordNotifier, never()).sendAlert(contains("장기 다운"), any())
        }

        @Test
        @DisplayName("알림 발사 후 복구되면 복구 알림이 발사되고 alertSent가 reset된다")
        fun recovery_sendsRecoveryAlertAndResetsFlag() {
            // 이전에 다운 알림이 발사된 상태 세팅
            setAlertSent(true)

            val onOpenLatch = CountDownLatch(1)
            doAnswer { onOpenLatch.countDown(); null }.whenever(handler).onOpen(any())

            sut.start()
            onOpenLatch.await(3, TimeUnit.SECONDS)
            Thread.sleep(500) // onOpen 내 복구 알림 발사까지 여유

            verify(discordNotifier).sendAlert(contains("복구"), any())
            assertThat(extractAlertSent()).isFalse()
        }
    }

    // 헬퍼

    /**
     * KisWebSocketClient.client (private WebSocketClient) 필드를 리플렉션으로 꺼낸다.
     */
    private fun extractReconnectAttempt(): Int {
        val field = KisWebSocketClient::class.java.getDeclaredField("reconnectAttempt")
        field.isAccessible = true
        return (field.get(sut) as AtomicInteger).get()
    }

    private fun setLastConnectedAt(instant: Instant) {
        val field = KisWebSocketClient::class.java.getDeclaredField("lastConnectedAt")
        field.isAccessible = true
        field.set(sut, instant)
    }

    private fun setAlertSent(value: Boolean) {
        val field = KisWebSocketClient::class.java.getDeclaredField("alertSent")
        field.isAccessible = true
        (field.get(sut) as AtomicBoolean).set(value)
    }

    private fun extractAlertSent(): Boolean {
        val field = KisWebSocketClient::class.java.getDeclaredField("alertSent")
        field.isAccessible = true
        return (field.get(sut) as AtomicBoolean).get()
    }

    // 테스트 전용 WebSocket 서버
    /**
     * org.java_websocket.server.WebSocketServer 기반 가짜 서버.
     * InetSocketAddress(0)으로 띄우면 OS가 랜덤 포트를 할당한다.
     */
    class TestWebSocketServer(port: Int = 0) : WebSocketServer(InetSocketAddress("127.0.0.1", port)) {
        private val startLatch = CountDownLatch(1)

        init { setReuseAddr(true) }

        fun waitUntilStarted() = startLatch.await(3, TimeUnit.SECONDS)

        override fun onStart() { startLatch.countDown() }
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {}
        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {}
        override fun onMessage(conn: WebSocket, message: String) {}
        override fun onError(conn: WebSocket?, ex: Exception) {}
    }
}

