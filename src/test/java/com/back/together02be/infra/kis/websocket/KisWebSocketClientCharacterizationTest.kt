package com.back.together02be.infra.kis.websocket

import com.back.together02be.infra.kis.config.KisProperties
import org.assertj.core.api.Assertions.assertThat
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * KisWebSocketClient 특성화 테스트 (Characterization Test)
 * 
 * 목적: 복구 로직 추가 이전에 현재 동작을 고정한다.
 * 이후 리팩터링/기능 추가 시 기존 동작이 깨지지 않음을 보장하는 안전망 용도
 * 
 * 전략: @SpringBootTest 없이 객체를 수동 생성.
 * 실제 WebSocket 연결은 로컬에 띄운 TestWebSocketServer로 대체.
 */
internal class KisWebSocketClientCharacterizationTest {

    // 가짜 서버 & SUT
    private lateinit var fakeServer: TestWebSocketServer
    private lateinit var sut: KisWebSocketClient

    // Mock/Stub
    private lateinit var kisProperties: KisProperties
    private lateinit var approvalKeyService: ApprovalKeyService
    private lateinit var handler: KisWebSocketHandler

    // Lifecycle
    @BeforeEach
    fun setUp() {
        // 1) 가짜 서버 기동 (OS가 랜덤 포트 할당)
        fakeServer = TestWebSocketServer()
        fakeServer.start()
        fakeServer.waitUntilStarted() // 서버가 실제로 준비될 때까지 대기

        val port = fakeServer.port // OS가 배정한 랜덤 포트 번호 획득

        // 2) KisProperties — 가짜 서버 URL을 반환하도록 stub
        kisProperties = mock<KisProperties>()
        whenever(kisProperties.wsUrl).thenReturn("ws://localhost:$port")

        // 3) ApprovalKeyService — 고정 키 반환
        approvalKeyService = mock<ApprovalKeyService>()
        whenever(approvalKeyService.getApprovalKey()).thenReturn("test-approval-key")

        // 4) KisWebSocketHandler — 호출 여부 검증용 mock
        handler = mock<KisWebSocketHandler>()

        // 5) SUT 수동 생성 (@PostConstruct인 connect()는 각 테스트에서 직접 호출)
        sut = KisWebSocketClient(kisProperties, approvalKeyService, handler)
    }

    @AfterEach
    fun tearDown() {
        // client 필드가 null일 수 있으므로 방어적으로 처리
        closeClientIfOpen()
        fakeServer.stop(1000)
    }

    // 테스트 케이스
    @Test
    @DisplayName("connect() 호출 시 ApprovalKey를 발급받아 handler.setApprovalKey()에 전달한다")
    fun connect_setsApprovalKeyOnHandler() {
        sut.connect()

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

        sut.connect()

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

        sut.connect()
        val opened = onOpenLatch.await(3, TimeUnit.SECONDS) // 연결 수립 후에 메시지를 받아야하므로 먼저 기다림
        assertThat(opened).`as`("메시지 전송 전에 연결이 수립되어야 한다").isTrue()

        fakeServer.broadcast("test-message-payload") // 가짜 서버가 메시지 전송

        val received = onMessageLatch.await(3, TimeUnit.SECONDS)
        assertThat(received).`as`("3초 내에 handler.onMessage()가 호출되어야 한다").isTrue()
        verify(handler).onMessage("test-message-payload")
    }

    @Test
    @DisplayName("subscribe() 호출 시 handler.subscribe()로 그대로 위임된다")
    @Throws(Exception::class)
    fun subscribe_delegatesToHandler() {
        // subscribe/unsubscribe는 handler로의 단순 위임이므로
        // onOpen 대기 없이 connect() 후 바로 검증
        sut.connect()
        sut.subscribe("005930")
        verify(handler).subscribe("005930")
    }

    @Test
    @DisplayName("unsubscribe() 호출 시 handler.unsubscribe()로 그대로 위임된다")
    @Throws(Exception::class)
    fun unsubscribe_delegatesToHandler() {
        sut.connect()
        sut.unsubscribe("005930")
        verify(handler).unsubscribe("005930")
    }

    @Test
    @DisplayName("서버가 연결을 종료하면 client.isOpen()이 false가 된다 (재연결 로직 없음을 간접 검증)")
    @Throws(Exception::class)
    fun serverClose_clientBecomesNotOpen() {
        val onOpenLatch = CountDownLatch(1)
        doAnswer { onOpenLatch.countDown(); null }.whenever(handler).onOpen(any())

        sut.connect()
        onOpenLatch.await(3, TimeUnit.SECONDS)

        // private client 필드에 리플렉션으로 접근
        val wsClient = extractClientField()
        assertThat(wsClient.isOpen).`as`("종료 전에는 연결이 열려 있어야 한다").isTrue()

        // 서버 강제 종료 → 클라이언트 측 연결 끊김 유발
        fakeServer.stop(500)

        // onClose 콜백이 전파될 때까지 잠시 대기
        Thread.sleep(500)

        // 현재 동작: onClose에서 재연결 시도 없음 → isOpen() == false
        assertThat(wsClient.isOpen).`as`("서버 종료 후 클라이언트는 닫혀야 한다").isFalse()
    }

    // 헬퍼

    /**
     * KisWebSocketClient.client (private WebSocketClient) 필드를 리플렉션으로 꺼낸다.
     */
    private fun extractClientField(): WebSocketClient {
        val field = KisWebSocketClient::class.java.getDeclaredField("client")
        field.isAccessible = true
        return field.get(sut) as WebSocketClient
    }

    /**
     * tearDown 시 client가 열려 있으면 닫는다.
     * connect()가 호출되지 않은 테스트에서는 field 자체가 null일 수 있다.
     */
    private fun closeClientIfOpen() {
        try {
            val wsClient = extractClientField()
            if (wsClient.isOpen) wsClient.closeBlocking()
        } catch (ignored: Exception) {
            // connect() 미호출 시 field == null → 무시
        }
    }

    // 테스트 전용 WebSocket 서버

    /**
     * org.java_websocket.server.WebSocketServer 기반 가짜 서버.
     * InetSocketAddress(0)으로 띄우면 OS가 랜덤 포트를 할당한다.
     */
    class TestWebSocketServer : WebSocketServer(InetSocketAddress(0)) {
        private val startLatch = CountDownLatch(1)

        fun waitUntilStarted() {
            startLatch.await(3, TimeUnit.SECONDS)
        }

        override fun onStart() { startLatch.countDown() }
        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {}
        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {}
        override fun onMessage(conn: WebSocket, message: String) {}
        override fun onError(conn: WebSocket?, ex: Exception) {}
    }
}

