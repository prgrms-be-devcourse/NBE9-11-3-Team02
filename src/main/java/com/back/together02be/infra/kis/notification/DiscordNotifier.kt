package com.back.together02be.infra.kis.notification

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

private val log = LoggerFactory.getLogger(DiscordNotifier::class.java)

@Component
class DiscordNotifier(
    private val restClient: RestClient,
    @Value("\${discord.webhook.url:}") private val webhookUrl: String
) {
    fun sendAlert(title: String, message: String) {
        if (webhookUrl.isBlank()) {
            log.debug("Discord webhook URL 미설정, 알림 skip")
            return
        }
        try {
            val payload = mapOf("content" to "**$title**\n$message")
            restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity()
            log.info("Discord 알림 전송 완료: {}", title)
        } catch (e: Exception) {
            log.error("Discord 알림 전송 실패: {}", title, e)
        }
    }
}