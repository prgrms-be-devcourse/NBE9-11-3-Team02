package com.back.together02be.infra.kis.websocket;

import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.back.together02be.infra.kis.config.KisProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalKeyService {

	private final KisProperties kisProperties;
	private final RestClient restClient;

	// https://apiportal.koreainvestment.com/apiservice-apiservice?/oauth2/Approval
	public String getApprovalKey() {
		Map<String, String> requestBody = Map.of(
			"grant_type", "client_credentials",
			"appkey", kisProperties.getAppKey(),
			"secretkey", kisProperties.getAppSecret()
		);

		Map<String, String> response = restClient.post()
			.uri(kisProperties.getRestBaseUrl() + "/oauth2/Approval")
			.contentType(MediaType.APPLICATION_JSON)
			.body(requestBody)
			.retrieve()
			.body(new ParameterizedTypeReference<>() {});

		String approvalKey = response.get("approval_key");
		log.info("approval_key 발급 완료: {}", approvalKey);
		return approvalKey;
	}

}
