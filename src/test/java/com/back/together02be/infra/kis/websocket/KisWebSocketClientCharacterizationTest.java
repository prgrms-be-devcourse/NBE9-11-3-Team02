package com.back.together02be.infra.kis.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.back.together02be.infra.kis.config.KisProperties;

/**
 * KisWebSocketClient 특성화 테스트 (Characterization Test)
 *
 * 목적: 복구 로직 추가 이전에 현재 동작을 고정한다.
 *       이후 리팩터링/기능 추가 시 기존 동작이 깨지지 않음을 보장하는 안전망 용도
 *
 * 전략: @SpringBootTest 없이 객체를 수동 생성.
 *       실제 WebSocket 연결은 로컬에 띄운 TestWebSocketServer로 대체.
 */
class KisWebSocketClientCharacterizationTest {

	// 가짜 서버 & SUT

	private TestWebSocketServer fakeServer;
	private KisWebSocketClient sut;

	// Mock/Stub

	private KisProperties kisProperties;
	private ApprovalKeyService approvalKeyService;
	private KisWebSocketHandler handler;

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
		when(kisProperties.getWsUrl()).thenReturn("ws://localhost:" + port);

		// 3) ApprovalKeyService — 고정 키 반환
		approvalKeyService = mock(ApprovalKeyService.class);
		when(approvalKeyService.getApprovalKey()).thenReturn("test-approval-key");

		// 4) KisWebSocketHandler — 호출 여부 검증용 mock, KisWebSocketHandler 구현체 사용시 여러 의존성 필요-> mock
		handler = mock(KisWebSocketHandler.class);

		// 5) SUT 수동 생성 (@PostConstruct인 connect()는 각 테스트에서 직접 호출)
		// System Under Test — 지금 테스트하려는 대상 클래스
		sut = new KisWebSocketClient(kisProperties, approvalKeyService, handler);
	}

	@AfterEach
	void tearDown() throws Exception {
		// client 필드가 null일 수 있으므로 방어적으로 처리
		closeClientIfOpen();
		fakeServer.stop(1000);
	}

	// 테스트 케이스

	@Test
	@DisplayName("connect() 호출 시 ApprovalKey를 발급받아 handler.setApprovalKey()에 전달한다")
	void connect_setsApprovalKeyOnHandler() throws Exception {
		sut.connect();

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

		sut.connect();

		boolean triggered = onOpenLatch.await(3, TimeUnit.SECONDS); // latch 열릴 때까지 최대 3초 대기
		assertThat(triggered).as("3초 내에 handler.onOpen()이 호출되어야 한다").isTrue();
		verify(handler).onOpen(any(WebSocketClient.class));
	}

	@Test
	@DisplayName("서버가 메시지를 전송하면 handler.onMessage()가 동일 문자열로 호출된다")
	void serverMessage_isForwardedToHandlerOnMessage() throws Exception {
		CountDownLatch onOpenLatch = new CountDownLatch(1);
		CountDownLatch onMessageLatch = new CountDownLatch(1);

		doAnswer(inv -> { onOpenLatch.countDown(); return null; })
			.when(handler).onOpen(any());

		doAnswer(inv -> { onMessageLatch.countDown(); return null; })
			.when(handler).onMessage(any());

		sut.connect();
		boolean opened = onOpenLatch.await(3, TimeUnit.SECONDS); // 연결 수립 후에 메시지를 받아야하므로 먼저 기다림
		assertThat(opened).as("메시지 전송 전에 연결이 수립되어야 한다").isTrue();

		fakeServer.broadcast("test-message-payload"); // 가짜 서버가 메시지 전송

		boolean received = onMessageLatch.await(3, TimeUnit.SECONDS);
		assertThat(received).as("3초 내에 handler.onMessage()가 호출되어야 한다").isTrue();
		verify(handler).onMessage("test-message-payload");
	}

	@Test
	@DisplayName("subscribe() 호출 시 handler.subscribe()로 그대로 위임된다")
	void subscribe_delegatesToHandler() throws Exception {
		// subscribe/unsubscribe는 handler로의 단순 위임이므로
		// onOpen 대기 없이 connect() 후 바로 검증
		sut.connect();

		sut.subscribe("005930");

		verify(handler).subscribe("005930");
	}

	@Test
	@DisplayName("unsubscribe() 호출 시 handler.unsubscribe()로 그대로 위임된다")
	void unsubscribe_delegatesToHandler() throws Exception {
		sut.connect();

		sut.unsubscribe("005930");

		verify(handler).unsubscribe("005930");
	}

	@Test
	@DisplayName("서버가 연결을 종료하면 client.isOpen()이 false가 된다 (재연결 로직 없음을 간접 검증)")
	void serverClose_clientBecomesNotOpen() throws Exception {
		CountDownLatch onOpenLatch = new CountDownLatch(1);
		doAnswer(inv -> { onOpenLatch.countDown(); return null; })
			.when(handler).onOpen(any());

		sut.connect();
		onOpenLatch.await(3, TimeUnit.SECONDS);

		// private client 필드에 리플렉션으로 접근
		var wsClient = extractClientField();
		assertThat(wsClient.isOpen()).as("종료 전에는 연결이 열려 있어야 한다").isTrue();

		// 서버 강제 종료 → 클라이언트 측 연결 끊김 유발
		fakeServer.stop(500);

		// onClose 콜백이 전파될 때까지 잠시 대기
		Thread.sleep(500);

		// 현재 동작: onClose에서 재연결 시도 없음 → isOpen() == false
		assertThat(wsClient.isOpen()).as("서버 종료 후 클라이언트는 닫혀야 한다").isFalse();
	}

	// 헬퍼

	/**
	 * KisWebSocketClient.client (private WebSocketClient) 필드를 리플렉션으로 꺼낸다.
	 */
	private WebSocketClient extractClientField() throws Exception {
		var field = KisWebSocketClient.class.getDeclaredField("client");
		field.setAccessible(true);
		return (WebSocketClient) field.get(sut);
	}

	/**
	 * tearDown 시 client가 열려 있으면 닫는다.
	 * connect()가 호출되지 않은 테스트에서는 field 자체가 null일 수 있다.
	 */
	private void closeClientIfOpen() {
		try {
			var wsClient = extractClientField();
			if (wsClient != null && wsClient.isOpen()) {
				wsClient.closeBlocking();
			}
		} catch (Exception ignored) {
			// connect() 미호출 시 field == null → 무시
		}
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
			super(new InetSocketAddress(0));
		}

		public void waitUntilStarted() throws InterruptedException {
			startLatch.await(3, TimeUnit.SECONDS);
		}

		@Override
		public void onStart() {
			startLatch.countDown();
		}

		@Override
		public void onOpen(WebSocket conn, ClientHandshake handshake) {}

		@Override
		public void onClose(WebSocket conn, int code, String reason, boolean remote) {}

		@Override
		public void onMessage(WebSocket conn, String message) {}

		@Override
		public void onError(WebSocket conn, Exception ex) {}
	}
}

