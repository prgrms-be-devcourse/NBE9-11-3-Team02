package com.back.together02be.infra.kis.websocket;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.context.ApplicationEventPublisher;

import com.back.together02be.infra.kis.config.KisProperties;
import com.back.together02be.infra.kis.event.WebSocketReconnectedEvent;

/**
 * KisWebSocketClient 단위 테스트
 *
 * 다루는 흐름:
 *   - 초기 연결: ApprovalKey 발급, handler 콜백, 메시지 forwarding
 *   - 재연결: 끊김 감지 후 백오프, 재구독, 이벤트 발행
 *   - 종료: graceful shutdown 시 재연결 시도 중단
 *   - 위임: subscribe/unsubscribe의 handler로의 위임
 *
 * 전략: @SpringBootTest 없이 객체 수동 생성.
 *       실제 WebSocket 연결은 로컬 TestWebSocketServer로 대체.
 *       handler를 mock해서 호출 여부 검증.
 */
@Timeout(value = 15, unit = TimeUnit.SECONDS)
class KisWebSocketClientTest {

	// 가짜 서버 & SUT
	private TestWebSocketServer fakeServer;
	private KisWebSocketClient sut;

	// Mock/Stub
	private KisProperties kisProperties;
	private ApprovalKeyService approvalKeyService;
	private KisWebSocketHandler handler;
	private ApplicationEventPublisher eventPublisher;

	// Lifecycle
	@BeforeEach
	void setUp() throws Exception {
		// 1) 가짜 서버 기동 (OS가 랜덤 포트 할당)
		fakeServer = new TestWebSocketServer();
		fakeServer.start();
		fakeServer.waitUntilStarted(); // 서버가 실제로 준비될 때까지 대기

		int port = fakeServer.getPort(); // OS가 배정한 랜덤 포트 번호 획득

		// 2) KisProperties — 가짜 서버 URL을 반환하도록 stub
		kisProperties = mock(KisProperties.class);
		when(kisProperties.getWsUrl()).thenReturn("ws://127.0.0.1:" + port);

		// 3) ApprovalKeyService — 고정 키 반환
		approvalKeyService = mock(ApprovalKeyService.class);
		when(approvalKeyService.getApprovalKey()).thenReturn("test-approval-key");

		// 4) KisWebSocketHandler — 호출 여부 검증용 mock, KisWebSocketHandler 구현체 사용시 여러 의존성 필요-> mock
		handler = mock(KisWebSocketHandler.class);
		eventPublisher = mock(ApplicationEventPublisher.class);

		// 5) SUT 수동 생성 (@PostConstruct인 connect()는 각 테스트에서 직접 호출)
		// System Under Test — 지금 테스트하려는 대상 클래스
		sut = new KisWebSocketClient(kisProperties, approvalKeyService, handler, eventPublisher);
	}

	@AfterEach
	void tearDown() throws Exception {
		// client 필드가 null일 수 있으므로 방어적으로 처리
		if (sut != null) {
			try {
				sut.stop();
			} catch (Exception ignored) {
			}
		}
		fakeServer.stop(1000);
	}

	@Nested
	@DisplayName("초기 연결")
	class ConnectionTest {
		// 테스트 케이스
		@Test
		@DisplayName("start() 호출 시 ApprovalKey를 발급받아 handler.setApprovalKey()에 전달한다")
		void start_setsApprovalKeyOnHandler() throws Exception {
			sut.start();

			verify(approvalKeyService).getApprovalKey();
			verify(handler).setApprovalKey("test-approval-key");
		}

		@Test
		@DisplayName("연결 수립 후 handler.onOpen(client)이 WebSocketClient 인스턴스와 함께 호출된다")
		void connect_callsHandlerOnOpenWithWebSocketClientInstance() throws Exception {
			CountDownLatch onOpenLatch = new CountDownLatch(1);

			doAnswer(invocation -> { // latch를 푸는 조건 설정
				onOpenLatch.countDown(); // 비동기이기 때문에 latch 설정(latch = 빗장)
				return null;
			}).when(handler).onOpen(any());

			sut.start();

			boolean triggered = onOpenLatch.await(3, TimeUnit.SECONDS); // latch 열릴 때까지 최대 3초 대기
			assertThat(triggered).as("3초 내에 handler.onOpen()이 호출되어야 한다").isTrue();
			verify(handler).onOpen(any(WebSocketClient.class));
		}

		@Test
		@DisplayName("서버가 메시지를 전송하면 handler.onMessage()가 동일 문자열로 호출된다")
		void serverMessage_isForwardedToHandlerOnMessage() throws Exception {
			CountDownLatch onOpenLatch = new CountDownLatch(1);
			CountDownLatch onMessageLatch = new CountDownLatch(1);

			doAnswer(inv -> {
				onOpenLatch.countDown();
				return null;
			})
				.when(handler).onOpen(any());

			doAnswer(inv -> {
				onMessageLatch.countDown();
				return null;
			})
				.when(handler).onMessage(any());

			sut.start();
			boolean opened = onOpenLatch.await(3, TimeUnit.SECONDS); // 연결 수립 후에 메시지를 받아야하므로 먼저 기다림
			assertThat(opened).as("메시지 전송 전에 연결이 수립되어야 한다").isTrue();

			fakeServer.broadcast("test-message-payload"); // 가짜 서버가 메시지 전송

			boolean received = onMessageLatch.await(3, TimeUnit.SECONDS);
			assertThat(received).as("3초 내에 handler.onMessage()가 호출되어야 한다").isTrue();
			verify(handler).onMessage("test-message-payload");
		}
	}

	@Nested
	@DisplayName("재연결 동작")
	class ReconnectionTest {

		@Test
		@DisplayName("첫 연결에서는 WebSocketReconnectedEvent가 발행되지 않는다")
		void firstConnect_doesNotPublishReconnectedEvent() throws Exception {
			CountDownLatch onOpenLatch = new CountDownLatch(1);
			doAnswer(inv -> {
				onOpenLatch.countDown();
				return null;
			})
				.when(handler).onOpen(any());

			sut.start();
			onOpenLatch.await(3, TimeUnit.SECONDS);

			verify(eventPublisher, never()).publishEvent(any(WebSocketReconnectedEvent.class));
		}

		@Test
		@DisplayName("서버 끊김 후 재연결 시도가 예약된다")
		void serverClose_schedulesReconnect() throws Exception {
			CountDownLatch onOpenLatch = new CountDownLatch(1);
			doAnswer(inv -> {
				onOpenLatch.countDown();
				return null;
			})
				.when(handler).onOpen(any());

			sut.start();
			onOpenLatch.await(3, TimeUnit.SECONDS);

			fakeServer.stop(500);
			Thread.sleep(2000); // 백오프 1초 + 여유

			assertThat(extractReconnectAttempt())
				.as("재연결 시도 카운터가 증가해야 한다")
				.isGreaterThan(0);
		}

		@Test
		@DisplayName("재연결 성공 시 handler.resubscribeAll()이 호출되고 이벤트가 발행된다")
		void reconnect_callsResubscribeAndPublishesEvent() throws Exception {
			CountDownLatch firstOpen = new CountDownLatch(1);
			CountDownLatch secondOpen = new CountDownLatch(1);
			AtomicInteger openCount = new AtomicInteger(0);

			doAnswer(inv -> {
				int n = openCount.incrementAndGet();
				if (n == 1)
					firstOpen.countDown();
				if (n >= 2)
					secondOpen.countDown();
				return null;
			}).when(handler).onOpen(any());

			int port = fakeServer.getPort();
			sut.start();
			firstOpen.await(3, TimeUnit.SECONDS);

			// 서버 강제 종료
			fakeServer.stop(500);

			// 같은 포트로 새 서버 띄움
			fakeServer = new TestWebSocketServer(port);
			fakeServer.start();
			fakeServer.waitUntilStarted();

			// 재연결 대기 (백오프 1초 + 재연결 시간)
			boolean reconnected = secondOpen.await(5, TimeUnit.SECONDS);
			assertThat(reconnected).as("5초 내 재연결되어야 한다").isTrue();

			verify(handler, atLeast(2)).onOpen(any());
			verify(handler, atLeast(1)).resubscribeAll();
			verify(eventPublisher, atLeast(1))
				.publishEvent(any(WebSocketReconnectedEvent.class));
		}
	}

	@Nested
	@DisplayName("종료 동작")
	class ShutdownTest {

		@Test
		@DisplayName("stop() 후에는 서버가 끊겨도 재연결 시도하지 않는다")
		void afterStop_doesNotReconnect() throws Exception {
			CountDownLatch onOpenLatch = new CountDownLatch(1);
			doAnswer(inv -> { onOpenLatch.countDown(); return null; })
				.when(handler).onOpen(any());

			sut.start();
			onOpenLatch.await(3, TimeUnit.SECONDS);

			// graceful stop
			sut.stop();

			// 서버 종료
			fakeServer.stop(500);

			// 백오프 시간보다 충분히 대기
			Thread.sleep(2000);

			// 첫 연결 1번만 호출됐어야 함
			verify(handler, times(1)).onOpen(any());

			// reconnectAttempt 증가 안 했어야 함
			assertThat(extractReconnectAttempt())
				.as("stop() 이후 재연결 시도가 없어야 한다")
				.isEqualTo(0);
		}
	}

	@Nested
	@DisplayName("위임 동작")
	class DelegationTest {

		@Test
		@DisplayName("subscribe() 호출 시 handler.subscribe()로 그대로 위임된다")
		void subscribe_delegatesToHandler() throws Exception {
			// subscribe/unsubscribe는 handler로의 단순 위임이므로
			// onOpen 대기 없이 connect() 후 바로 검증
			sut.start();

			sut.subscribe("005930");

			verify(handler).subscribe("005930");
		}

		@Test
		@DisplayName("unsubscribe() 호출 시 handler.unsubscribe()로 그대로 위임된다")
		void unsubscribe_delegatesToHandler() throws Exception {
			sut.start();

			sut.unsubscribe("005930");

			verify(handler).unsubscribe("005930");
		}
	}

	// 헬퍼

	/**
	 * KisWebSocketClient.client (private WebSocketClient) 필드를 리플렉션으로 꺼낸다.
	 */
	private int extractReconnectAttempt() throws Exception {
		var field = KisWebSocketClient.class.getDeclaredField("reconnectAttempt");
		field.setAccessible(true);
		AtomicInteger counter = (AtomicInteger)field.get(sut);
		return counter.get();
	}

	// 테스트 전용 WebSocket 서버

	/**
	 * org.java_websocket.server.WebSocketServer 기반 가짜 서버.
	 * new InetSocketAddress(0)으로 띄우면 OS가 랜덤 포트를 할당한다.
	 * 실제 동작: 연결 수락 + 메시지 브로드캐스트만 수행.
	 */
	static class TestWebSocketServer extends WebSocketServer {

		private final CountDownLatch startLatch = new CountDownLatch(1);

		TestWebSocketServer() {
			this(0);
		}

		TestWebSocketServer(int port) {
			super(new InetSocketAddress("127.0.0.1", port));
			setReuseAddr(true); // 재시작 시 같은 포트 재바인딩 허용
		}

		public void waitUntilStarted() throws InterruptedException {
			startLatch.await(3, TimeUnit.SECONDS);
		}

		@Override
		public void onStart() {
			startLatch.countDown();
		}

		@Override
		public void onOpen(WebSocket conn, ClientHandshake handshake) {
		}

		@Override
		public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		}

		@Override
		public void onMessage(WebSocket conn, String message) {
		}

		@Override
		public void onError(WebSocket conn, Exception ex) {
		}
	}
}

