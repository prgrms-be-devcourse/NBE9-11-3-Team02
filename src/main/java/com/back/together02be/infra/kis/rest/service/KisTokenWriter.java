package com.back.together02be.infra.kis.rest.service;

import com.back.together02be.infra.kis.rest.dto.KisTokenRes;
import com.back.together02be.infra.kis.rest.entity.KisAccessToken;
import com.back.together02be.infra.kis.rest.repository.KisAccessTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class KisTokenWriter {

    private final KisAccessTokenRepository kisAccessTokenRepository;

    @Transactional
    public String saveTokenToDb(KisTokenRes tokenResponse) {
        if (tokenResponse == null
                || tokenResponse.accessToken() == null
                || tokenResponse.expiresIn() == null) {
            throw new IllegalStateException("KIS 토큰 발급 응답이 올바르지 않습니다.");
        }

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(tokenResponse.expiresIn());

        KisAccessToken tokenEntity = kisAccessTokenRepository.findTopByOrderByIdDesc().orElse(null);

        if (tokenEntity == null) {
            tokenEntity = new KisAccessToken(tokenResponse.accessToken(), tokenResponse.tokenType(), expiresAt);
        } else {
            tokenEntity.update(tokenResponse.accessToken(), tokenResponse.tokenType(), expiresAt);
        }

        kisAccessTokenRepository.save(tokenEntity);
        log.info("KIS 접근 토큰 신규 발급 및 저장 완료. expiresAt={}", expiresAt);

        return tokenEntity.getAccessToken();
    }
}