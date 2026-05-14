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

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    @Value("${kis.rest-base-url}")
    private String restBaseUrl;

    private final KisAccessTokenRepository kisAccessTokenRepository;

    // RestClient는 별도 Bean 없이 현재 구조 유지
    private final RestClient restClient = RestClient.create();

    // 사용 가능한 토큰이 있으면 재사용, 없으면 새로 발급
    @Transactional
    public synchronized String getAccessToken() {
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

    // 토큰 발급 실패 시 지터 방식으로 무한 재시도
    private String issueAndSaveNewTokenWithRetry() {
        int attempt = 1;

        while (true) {
            try {
                // 토큰 발급 성공 시 즉시 반환
                return issueAndSaveNewToken();

            } catch (HttpClientErrorException.Forbidden e) {
                String responseBody = e.getResponseBodyAsString();

                // 발급 제한 오류면 지터 대기 후 재시도
                if (responseBody != null && responseBody.contains("EGW00133")) {
                    log.warn("KIS 접근 토큰 발급 제한 응답 발생 (attempt={}). 지터 대기 후 재시도합니다. body={}",
                            attempt, responseBody);

                    sleepRetryInterval(attempt);
                    attempt++;
                    continue;
                }

                // 발급 제한이 아닌 403은 그대로 예외 처리
                throw e;

            } catch (Exception e) {
                log.warn("KIS 접근 토큰 발급 실패 (attempt={}). 지터 대기 후 재시도합니다. 원인={}",
                        attempt, e.getMessage());

                sleepRetryInterval(attempt);
                attempt++;
            }
        }
    }

    // KIS 토큰 발급 API를 호출하고 DB에 저장한다.
    @Transactional
    protected String issueAndSaveNewToken() {
        String url = restBaseUrl + "/oauth2/tokenP";

        //토근 발급 요청 바디
        Map<String, String> requestBody = Map.of(
                "grant_type", "client_credentials",
                "appkey", appKey,
                "appsecret", appSecret
        );

        //KIS 토큰 발급 API 호출
        KisTokenRes tokenResponse = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(KisTokenRes.class);

        //응답이 비정상이면 예외 처리
        if (tokenResponse == null || tokenResponse.accessToken() == null) {
            throw new IllegalStateException("토큰 발급 실패");
        }

        //만료 시각 계산
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(tokenResponse.expiresIn() == null ? 0 : tokenResponse.expiresIn());

        //최근 토큰 레코드 조회
        KisAccessToken tokenEntity = kisAccessTokenRepository.findTopByOrderByIdDesc()
                .orElse(null);

        //최근 토큰이 없으면 insert
        if (tokenEntity == null) {
            tokenEntity = new KisAccessToken(
                    tokenResponse.accessToken(),
                    tokenResponse.tokenType(),
                    expiresAt
            );
        } else {
            //최근 토큰이 있으면 update
            tokenEntity.update(
                    tokenResponse.accessToken(),
                    tokenResponse.tokenType(),
                    expiresAt
            );
        }

        //DB 저장
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