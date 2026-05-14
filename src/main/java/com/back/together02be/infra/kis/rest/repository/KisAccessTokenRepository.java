package com.back.together02be.infra.kis.rest.repository;

import com.back.together02be.infra.kis.rest.entity.KisAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KisAccessTokenRepository extends JpaRepository<KisAccessToken, Long> {

    // 가장 최근에 저장된 토큰 1개 조회
    Optional<KisAccessToken> findTopByOrderByIdDesc();
}