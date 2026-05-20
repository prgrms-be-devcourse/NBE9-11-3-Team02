package com.back.together02be.infra.kis.websocket;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.back.together02be.infra.kis.config.KisProperties;
import com.back.together02be.infra.kis.event.WebSocketReconnectedEvent;
import com.back.together02be.infra.notification.DiscordNotifier;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisWebSocketClient {

	private enum State {CONNECTING, CONNECTED, SHUTTING_DOWN}

	private static final long INITIAL_DELAY_MS = 1_000; // 첫 재연결 대기 시간 (1초)
	private static final long MAX_DELAY_MS = 30_000; // 알림 발사 전 재연결 최대 대기 시간 (30초)
	private static final double BACKOFF_MULTIPLIER = 2.0; // 재시도마다 대기 시간 증가 배수
	private static final double JITTER_FACTOR = 0.2; // 동시 재연결 분산을 위한 랜덤 오차 범위 (±20%)
	private static final Duration ALERT_THRESHOLD = Duration.ofMinutes(5); // 연결 끊긴 후 알림 전송까지 시간
	private static final long ALERT_BACKOFF_MS = 2 * 60_000L; // 알림 발사 후 재연결 시도 간격 상한 (2분마다 재시도)

	private final KisProperties kisProperties;
	private final ApprovalKeyService approvalKeyService;
	private final KisWebSocketHandler handler;
	private final ApplicationEventPublisher eventPublisher;
	private final DiscordNotifier discordNotifier;

	private WebSocketClient client;

	private final AtomicReference<State> state = new AtomicReference<>(State.CONNECTING);
	private final AtomicInteger reconnectAttempt = new AtomicInteger(0);
	private ScheduledExecutorService reconnectScheduler;
	private volatile Instant lastConnectedAt = Instant.now();
	private final AtomicBoolean alertSent = new AtomicBoolean(false);

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
					Instant now = Instant.now();
					Duration downtime = Duration.between(lastConnectedAt, now);

					state.set(State.CONNECTED);
					reconnectAttempt.set(0);
					lastConnectedAt = now;

					handler.onOpen(this);
					handler.resubscribeAll(); // 첫 연결 시 no-op 작동 x, 재연결 시 복원

					if (wasReconnect) {
						eventPublisher.publishEvent(new WebSocketReconnectedEvent());
					}

					if (alertSent.compareAndSet(true, false)) {
						discordNotifier.sendAlert(
							"KIS WebSocket 복구",
							"다운타임: " + formatDuration(downtime)
						);
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

		Duration downtime = Duration.between(lastConnectedAt, Instant.now());
		if (downtime.compareTo(ALERT_THRESHOLD) >= 0
			&& alertSent.compareAndSet(false, true)) {
			discordNotifier.sendAlert(
				"KIS WebSocket 장기 다운",
				String.format(
					"다운타임: %s\n재시도 횟수: %d\n자동 재시도는 계속됩니다.",
					formatDuration(downtime), attempt
				)
			);
		}
	}

	private String formatDuration(Duration d) {
		long minutes = d.toMinutes();
		long seconds = d.minusMinutes(minutes).getSeconds();
		return String.format("%d분 %d초", minutes, seconds);
	}

	private long calculateBackoff(int attempt) {
		long maxDelay = alertSent.get() ? ALERT_BACKOFF_MS : MAX_DELAY_MS;
		double base = Math.min(maxDelay,
			INITIAL_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, attempt - 1));
		double jitter = 1 + (ThreadLocalRandom.current().nextDouble() * 2 - 1) * JITTER_FACTOR;
		return (long)(base * jitter);
	}

	public void subscribe(String stockCode) {
		handler.subscribe(stockCode);
	}

	public void unsubscribe(String stockCode) {
		handler.unsubscribe(stockCode);
	}
}
