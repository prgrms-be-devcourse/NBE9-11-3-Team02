package com.back.together02be.infra.kis.websocket

import com.back.together02be.infra.kis.config.KisProperties
import jakarta.annotation.PostConstruct
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI

private val log = LoggerFactory.getLogger(KisWebSocketClient::class.java)

@Component
class KisWebSocketClient(
    private val kisProperties: KisProperties,
    private val approvalKeyService: ApprovalKeyService,
    private val handler: KisWebSocketHandler
) {
    private lateinit var client: WebSocketClient

    @PostConstruct
    fun connect() {
        val approvalKey = approvalKeyService.getApprovalKey()
        handler.setApprovalKey(approvalKey)

        client = object : WebSocketClient(URI(kisProperties.wsUrl)) {
            override fun onOpen(handshake: ServerHandshake) {
                log.info("한국투자 증권 WebSocket 연결 성공")
                handler.onOpen(this)
            }

            override fun onMessage(message: String) {
                handler.onMessage(message)
            }

            override fun onClose(code: Int, reason: String, remote: Boolean) {
                log.warn("WebSocket 연결 종료 - code: {}, reason: {}", code, reason)
            }

            override fun onError(e: Exception) {
                log.error("WebSocket 오류", e)
            }
        }

        client.connectBlocking()
    }

    fun subscribe(stockCode: String) = handler.subscribe(stockCode)

    fun unsubscribe(stockCode: String) = handler.unsubscribe(stockCode)
}
