package com.back.together02be.infra.kis.rest.service;

import com.back.together02be.infra.kis.rest.dto.KisTokenRes;
import com.back.together02be.infra.kis.rest.entity.KisAccessToken;
import com.back.together02be.infra.kis.rest.repository.KisAccessTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class KisTokenService {

    // 기본 대기 시간 60초
    private static final long BASE_RETRY_WAIT_MILLIS = 60_000L;

    // 지터 범위 ±10초
    private static final long JITTER_MILLIS = 10_000L;

    //재시도 횟수 제한
    private static final int MAX_RETRIES = 3;

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    @Value("${kis.rest-base-url}")
    private String restBaseUrl;

    private final KisAccessTokenRepository kisAccessTokenRepository;

    private final RestClient restClient;

    // 사용 가능한 토큰이 있으면 재사용, 없으면 새로 발급
    public String getAccessToken() {
        KisAccessToken savedToken = kisAccessTokenRepository.findTopByOrderByIdDesc()
                .orElse(null);

        //유효한 최근 토큰이 있으면 그대로 반환
        if (savedToken != null && savedToken.isUsable()) {
            log.info("KIS 접근 토큰 재사용. expiresAt={}", savedToken.getExpiresAt());
            return savedToken.getAccessToken();
        }

        //유효한 토큰이 없으면 재시도 포함 신규 발급
        return issueAndSaveNewTokenWithRetry();
    }

    private String issueAndSaveNewTokenWithRetry() {
        // 💡 while(true) 대신 최대 3번까지만 도는 for문으로 변경
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // 💡 [중요] 3단계에서 쪼갤 메서드 두 개를 여기서 호출하도록 변경!
                KisTokenRes tokenResponse = fetchTokenFromApi(); // 외부 API 호출 (트랜잭션 X)
                return saveTokenToDb(tokenResponse);             // DB 저장 (트랜잭션 O)

            } catch (HttpClientErrorException.Forbidden e) {
                String responseBody = e.getResponseBodyAsString();
                if (responseBody != null && responseBody.contains("EGW00133")) {
                    log.warn("KIS 접근 토큰 발급 제한 응답 발생 (attempt={}/{}).", attempt, MAX_RETRIES);
                    if (attempt == MAX_RETRIES) throw e; // 💡 3번 꽉 채우면 예외 던짐
                    sleepRetryInterval(attempt);
                    continue;
                }
                throw e;
            } catch (Exception e) {
                log.warn("KIS 접근 토큰 발급 실패 (attempt={}/{}).", attempt, MAX_RETRIES);
                if (attempt == MAX_RETRIES) {
                    throw new IllegalStateException("토큰 발급 최대 재시도 횟수 초과", e); // 💡 실패 처리
                }
                sleepRetryInterval(attempt);
            }
        }
        throw new IllegalStateException("토큰 발급 실패");
    }

    // 1. 외부 API 통신 전용 (트랜잭션 없음!)
    private KisTokenRes fetchTokenFromApi() {
        String url = restBaseUrl + "/oauth2/tokenP";
        Map<String, String> requestBody = Map.of(
                "grant_type", "client_credentials",
                "appkey", appKey,
                "appsecret", appSecret
        );

        // KIS API 호출 후 데이터만 받아옴 (DB 커넥션 사용 안 함)
        return restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(KisTokenRes.class);
    }

    // 2. DB 저장 전용 (여기서만 트랜잭션 사용)
    @Transactional
    protected String saveTokenToDb(KisTokenRes tokenResponse) {
        if (tokenResponse == null || tokenResponse.accessToken() == null) {
            throw new IllegalStateException("토큰 발급 실패");
        }

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(tokenResponse.expiresIn() == null ? 0 : tokenResponse.expiresIn());

        KisAccessToken tokenEntity = kisAccessTokenRepository.findTopByOrderByIdDesc().orElse(null);

        if (tokenEntity == null) {
            tokenEntity = new KisAccessToken(tokenResponse.accessToken(), tokenResponse.tokenType(), expiresAt);
        } else {
            tokenEntity.update(tokenResponse.accessToken(), tokenResponse.tokenType(), expiresAt);
        }

        // 통신이 다 끝나고 결과를 저장하는 순간에만 DB 커넥션 사용
        kisAccessTokenRepository.save(tokenEntity);
        log.info("KIS 접근 토큰 신규 발급 및 저장 완료. expiresAt={}", expiresAt);

        return tokenEntity.getAccessToken();
    }

    private void sleepRetryInterval(int attempt) {
        long jitter = ThreadLocalRandom.current()
                .nextLong(-JITTER_MILLIS, JITTER_MILLIS + 1);

        long waitTime = BASE_RETRY_WAIT_MILLIS + jitter;

        log.info("토큰 재시도 대기 시간: {}ms (attempt={})", waitTime, attempt);

        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("토큰 재시도 대기 중 인터럽트 발생", e);
        }
    }
}