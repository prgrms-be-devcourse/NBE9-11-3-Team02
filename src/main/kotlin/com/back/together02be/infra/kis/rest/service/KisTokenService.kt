package com.back.together02be.infra.kis.rest.service

import com.back.together02be.infra.kis.rest.dto.KisTokenRes
import com.back.together02be.infra.kis.rest.entity.KisAccessToken
import com.back.together02be.infra.kis.rest.repository.KisAccessTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.HttpClientErrorException.Forbidden
import org.springframework.web.client.RestClient
import java.time.LocalDateTime
import java.util.concurrent.ThreadLocalRandom

@Service
class KisTokenService(
    private val restClient: RestClient,
    private val kisAccessTokenRepository: KisAccessTokenRepository,
    @Value("\${kis.app-key}") private val appKey: String,
    @Value("\${kis.app-secret}") private val appSecret: String,
    @Value("\${kis.rest-base-url}") private val restBaseUrl: String
) {
    private val log = LoggerFactory.getLogger(KisTokenService::class.java)

    @Synchronized
    @Transactional
    fun getAccessToken(): String {
        val savedToken = kisAccessTokenRepository.findTopByOrderByIdDesc()

        if (savedToken != null && savedToken.isUsable) {
            log.info("KIS 접근 토큰 재사용. expiresAt={}", savedToken.expiresAt)
            return savedToken.accessToken
        }

        return issueAndSaveNewTokenWithRetry()
    }

    private fun issueAndSaveNewTokenWithRetry(): String {
        var attempt = 1

        while (true) {
            try {
                return issueAndSaveNewToken()
            } catch (e: Forbidden) {
                val responseBody = e.responseBodyAsString

                if (responseBody.contains("EGW00133")) {
                    log.warn("KIS 접근 토큰 발급 제한 응답 발생 (attempt={}). 지터 대기 후 재시도합니다. body={}", attempt, responseBody)
                    sleepRetryInterval(attempt)
                    attempt++
                    continue
                }
                throw e
            } catch (e: Exception) {
                log.warn("KIS 접근 토큰 발급 실패 (attempt={}). 지터 대기 후 재시도합니다. 원인={}", attempt, e.message)
                sleepRetryInterval(attempt)
                attempt++
            }
        }
    }

    private fun issueAndSaveNewToken(): String {
        val url = "$restBaseUrl/oauth2/tokenP"

        val requestBody = mapOf(
            "grant_type" to "client_credentials",
            "appkey" to appKey,
            "appsecret" to appSecret
        )

        val tokenResponse = restClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody)
            .retrieve()
            .body(KisTokenRes::class.java)

        val validAccessToken = tokenResponse?.accessToken ?: throw IllegalStateException("토큰 발급 실패")

        val expiresIn = tokenResponse.expiresIn?.toLong() ?: 0L
        val expiresAt = LocalDateTime.now().plusSeconds(expiresIn)

        val tokenEntity = kisAccessTokenRepository.findTopByOrderByIdDesc()

        return tokenEntity?.let {
            it.update(
                accessToken = validAccessToken,
                tokenType = tokenResponse.tokenType ?: "Bearer",
                expiresAt = expiresAt
            )
            kisAccessTokenRepository.save(it)
            log.info("KIS 접근 토큰 갱신 및 저장 완료. expiresAt={}", expiresAt)
            it.accessToken
        } ?: run {
            val newToken = KisAccessToken(
                accessToken = validAccessToken,
                tokenType = tokenResponse.tokenType ?: "Bearer",
                expiresAt = expiresAt
            )
            kisAccessTokenRepository.save(newToken)
            log.info("KIS 접근 토큰 신규 발급 및 저장 완료. expiresAt={}", expiresAt)
            newToken.accessToken
        }
    }

    private fun sleepRetryInterval(attempt: Int) {
        val jitter = ThreadLocalRandom.current().nextLong(-JITTER_MILLIS, JITTER_MILLIS + 1)
        val waitTime = BASE_RETRY_WAIT_MILLIS + jitter

        log.info("토큰 재시도 대기 시간: {}ms (attempt={})", waitTime, attempt)

        try {
            Thread.sleep(waitTime)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("토큰 재시도 대기 중 인터럽트 발생", e)
        }
    }

    companion object {
        private const val BASE_RETRY_WAIT_MILLIS = 60000L
        private const val JITTER_MILLIS = 10000L
    }
}