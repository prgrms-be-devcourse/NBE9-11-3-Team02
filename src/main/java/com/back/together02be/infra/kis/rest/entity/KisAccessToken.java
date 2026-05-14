package com.back.together02be.infra.kis.rest.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "kis_access_token")
public class KisAccessToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 실제 access token 값
    @Column(nullable = false, length = 2000)
    private String accessToken;

    // 보통 Bearer
    @Column(nullable = false, length = 50)
    private String tokenType;

    // 토큰 만료 시각
    @Column(nullable = false)
    private LocalDateTime expiresAt;

    public KisAccessToken(String accessToken, String tokenType, LocalDateTime expiresAt) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
    }

    public void update(String accessToken, String tokenType, LocalDateTime expiresAt) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
    }

    // 만료 직전 에러 방지를 위해 1분 여유를 둔다.
    public boolean isUsable() {
        return expiresAt.isAfter(LocalDateTime.now().plusMinutes(1));
    }
}