package com.back.together02be.infra.kis.websocket;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.back.together02be.infra.kis.config.KisProperties;
import com.back.together02be.infra.kis.event.WebSocketReconnectedEvent;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisWebSocketClient {

	private enum State {CONNECTING, CONNECTED, SHUTTING_DOWN}

	private static final long INITIAL_DELAY_MS = 1_000;
	private static final long MAX_DELAY_MS = 30_000;
	private static final double BACKOFF_MULTIPLIER = 2.0;
	private static final double JITTER_FACTOR = 0.2;

	private final KisProperties kisProperties;
	private final ApprovalKeyService approvalKeyService;
	private final KisWebSocketHandler handler;
	private final ApplicationEventPublisher eventPublisher;

	private WebSocketClient client;

	private final AtomicReference<State> state = new AtomicReference<>(State.CONNECTING);
	private final AtomicInteger reconnectAttempt = new AtomicInteger(0);
	private ScheduledExecutorService reconnectScheduler;

	@PostConstruct
	public void start() {
		reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "kis-reconnect");
			t.setDaemon(true);
			return t;
		});
		connect();
	}

	private void connect() {
		if (state.get() == State.SHUTTING_DOWN)
			return;

		try {
			String approvalKey = approvalKeyService.getApprovalKey();
			handler.setApprovalKey(approvalKey);

			client = new WebSocketClient(new URI(kisProperties.getWsUrl())) {

				@Override
				public void onOpen(ServerHandshake handshake) {
					log.info("한국투자 증권 WebSocket 연결 성공");
					boolean wasReconnect = reconnectAttempt.get() > 0;
					state.set(State.CONNECTED);
					reconnectAttempt.set(0);
					handler.onOpen(this);
					handler.resubscribeAll(); // 첫 연결 시 no-op 작동 x, 재연결 시 복원

					if (wasReconnect) {
						eventPublisher.publishEvent(new WebSocketReconnectedEvent());
					}
				}

				@Override
				public void onMessage(String message) {
					handler.onMessage(message);
				}

				@Override
				public void onClose(int code, String reason, boolean remote) {
					log.warn("WebSocket 연결 종료 - code: {}, reason: {}", code, reason);
					scheduleReconnect();
				}

				@Override
				public void onError(Exception e) {
					log.error("WebSocket 오류", e);
					scheduleReconnect();
				}
			};

			boolean connected = client.connectBlocking();
			if (!connected) {
				log.warn("WebSocket connectBlocking 실패 (시도 #{})", reconnectAttempt.get());
				scheduleNextAttempt();
			}

		} catch (Exception e) {
			log.error("WebSocket 연결 실패 (시도 #{})", reconnectAttempt.get(), e);
			scheduleNextAttempt();
		}

	}

	@PreDestroy
	public void stop() {
		state.set(State.SHUTTING_DOWN); // 먼저 상태 변경

		if (client != null && client.isOpen()) {
			client.close(); // 연결 종료
		}

		reconnectScheduler.shutdown(); // 스케줄러 종료
		try {
			if (!reconnectScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
				reconnectScheduler.shutdownNow();
			}
		} catch (InterruptedException e) {
			reconnectScheduler.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private void scheduleReconnect() {
		if (!state.compareAndSet(State.CONNECTED, State.CONNECTING)) {
			return;
		}
		scheduleNextAttempt();
	}

	private void scheduleNextAttempt() {
		if (state.get() == State.SHUTTING_DOWN) return;

		int attempt = reconnectAttempt.incrementAndGet();
		long delay = calculateBackoff(attempt);
		log.info("재연결 예약 - 시도 #{}, {}ms 후", attempt, delay);
		reconnectScheduler.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
	}

	private long calculateBackoff(int attempt) {
		double base = Math.min(MAX_DELAY_MS,
			INITIAL_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, attempt - 1));
		double jitter = 1 + (Math.random() * 2 - 1) * JITTER_FACTOR;
		return (long)(base * jitter);
	}

	public void subscribe(String stockCode) {
		handler.subscribe(stockCode);
	}

	public void unsubscribe(String stockCode) {
		handler.unsubscribe(stockCode);
	}
}
