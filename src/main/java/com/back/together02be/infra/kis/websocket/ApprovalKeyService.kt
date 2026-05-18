package com.back.together02be.infra.kis.websocket

import com.back.together02be.infra.kis.config.KisProperties
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

private val log = LoggerFactory.getLogger(ApprovalKeyService::class.java)

@Service
class ApprovalKeyService(
    private val kisProperties: KisProperties,
    private val restClient: RestClient
) {

    // https://apiportal.koreainvestment.com/apiservice-apiservice?/oauth2/Approval
    fun getApprovalKey(): String {
        val requestBody = mapOf(
            "grant_type" to "client_credentials",
            "appkey" to kisProperties.appKey,
            "secretkey" to kisProperties.appSecret
        )

        val response = restClient.post()
            .uri("${kisProperties.restBaseUrl}/oauth2/Approval")
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, String>>() {})
            ?: error("KIS approval_key 응답이 null입니다")

        val approvalKey = response["approval_key"]
            ?: error("응답에 approval_key 필드가 없습니다")

        log.info("approval_key 발급 완료: $approvalKey")
        return approvalKey
    }
}
