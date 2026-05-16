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

// Kotlin 전환 포인트:
// 1. Lombok의 @RequiredArgsConstructor를 제거하고 주 생성자(Primary Constructor)로 의존성 주입.
// 2. Lombok의 @Slf4j를 제거하고 코틀린의 표준 로거 선언 방식으로 변경하여 컴파일 속도와 안정성 향상.
@Service
class KisTokenService(
    private val kisAccessTokenRepository: KisAccessTokenRepository,
    @Value("\${kis.app-key}") private val appKey: String,
    @Value("\${kis.app-secret}") private val appSecret: String,
    @Value("\${kis.rest-base-url}") private val restBaseUrl: String
) {
    private val log = LoggerFactory.getLogger(KisTokenService::class.java)
    private val restClient: RestClient = RestClient.create()

    // Kotlin 전환 포인트:
    // 자바 시절의 프로퍼티 꼼수(@get:Synchronized 등) 대신 명확한 명사형 메서드로 분리.
    // 외부에서 kisTokenService.getAccessToken() 으로 아주 자연스럽게 호출 가능.
    @Synchronized
    @Transactional
    fun getAccessToken(): String {
        // Kotlin 전환 포인트:
        // 앞서 Repository를 코틀린으로 바꿨기 때문에 무거운 Optional.orElse(null)이 필요 없음.
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
                // Kotlin 전환 포인트: getter 메서드(getResponseBodyAsString) 대신 프로퍼티 접근법 사용
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

    @Transactional
    protected fun issueAndSaveNewToken(): String {
        val url = "$restBaseUrl/oauth2/tokenP"

        // Kotlin 전환 포인트:
        // Map.of() 대신 훨씬 직관적인 mapOf("key" to "value") 문법 사용
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

        // Kotlin 전환 포인트:
        // 확실한 Null 처리 및 스마트 캐스팅을 위한 Elvis 연산자(?:) 활용.
        // throw를 사용하여 NPE 방지 및 명확한 에러 핸들링.
        val validAccessToken = tokenResponse?.accessToken ?: throw IllegalStateException("토큰 발급 실패")

        // Kotlin 전환 포인트:
        // 엘비스 연산자(?:)를 사용하여 null일 경우 기본값(0)을 세팅하는 로직을 한 줄로 압축
        val expiresIn = tokenResponse.expiresIn?.toLong() ?: 0L
        val expiresAt = LocalDateTime.now().plusSeconds(expiresIn)

        val tokenEntity = kisAccessTokenRepository.findTopByOrderByIdDesc()

        if (tokenEntity == null) {
            val newToken = KisAccessToken(
                accessToken = validAccessToken,
                tokenType = tokenResponse.tokenType ?: "Bearer",
                expiresAt = expiresAt
            )
            kisAccessTokenRepository.save(newToken)
            log.info("KIS 접근 토큰 신규 발급 및 저장 완료. expiresAt={}", expiresAt)
            return newToken.accessToken
        } else {
            tokenEntity.update(
                accessToken = validAccessToken,
                tokenType = tokenResponse.tokenType ?: "Bearer",
                expiresAt = expiresAt
            )
            kisAccessTokenRepository.save(tokenEntity)
            log.info("KIS 접근 토큰 갱신 및 저장 완료. expiresAt={}", expiresAt)
            return tokenEntity.accessToken
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

    // Kotlin 전환 포인트:
    // 상수는 companion object 안으로 이동하여 메모리 효율성 및 응집도 향상
    companion object {
        private const val BASE_RETRY_WAIT_MILLIS = 60000L
        private const val JITTER_MILLIS = 10000L
    }
}