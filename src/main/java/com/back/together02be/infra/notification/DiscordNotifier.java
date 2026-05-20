package com.back.together02be.infra.notification;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DiscordNotifier {

	private final RestClient restClient;
	private final String webhookUrl;

	public DiscordNotifier(
		RestClient restClient,
		@Value("${discord.webhook.url:}") String webhookUrl
	) {
		this.restClient = restClient;
		this.webhookUrl = webhookUrl;
	}

	public void sendAlert(String title, String message) {
		if (webhookUrl.isBlank()) {
			log.debug("Discord webhook URL 미설정, 알림 skip");
			return;
		}

		try {
			Map<String, Object> payload = Map.of(
				"content", "**" + title + "**\n" + message
			);

			restClient.post()
				.uri(webhookUrl)
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.retrieve()
				.toBodilessEntity();

			log.info("Discord 알림 전송 완료: {}", title);
		} catch (Exception e) {
			log.error("Discord 알림 전송 실패: {}", title, e);
		}
	}
}
