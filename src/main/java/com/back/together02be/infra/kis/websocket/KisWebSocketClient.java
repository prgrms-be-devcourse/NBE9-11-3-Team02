package com.back.together02be.infra.kis.websocket;

import java.net.URI;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Component;

import com.back.together02be.infra.kis.config.KisProperties;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisWebSocketClient {

	private final KisProperties kisProperties;
	private final ApprovalKeyService approvalKeyService;
	private final KisWebSocketHandler handler;

	private WebSocketClient client;

	@PostConstruct
	public void connect() throws Exception {
		String approvalKey = approvalKeyService.getApprovalKey();
		handler.setApprovalKey(approvalKey);

		client = new WebSocketClient(new URI(kisProperties.getWsUrl())) {

			@Override
			public void onOpen(ServerHandshake handshake) {
				log.info("한국투자 증권 WebSocket 연결 성공");
				handler.onOpen(this);
			}

			@Override
			public void onMessage(String message) {
				handler.onMessage(message);
			}

			@Override
			public void onClose(int code, String reason, boolean remote) {
				log.warn("WebSocket 연결 종료 - code: {}, reason: {}", code, reason);
			}

			@Override
			public void onError(Exception e) {
				log.error("WebSocket 오류", e);
			}
		};

		client.connectBlocking();
	}

	public void subscribe(String stockCode) {
		handler.subscribe(stockCode);
	}

	public void unsubscribe(String stockCode) {
		handler.unsubscribe(stockCode);
	}
}
