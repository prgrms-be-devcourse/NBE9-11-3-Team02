package com.back.together02be.users.entity;

import com.back.together02be.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class Users extends BaseEntity {

	@Column(nullable = false, unique = true, length = 30)
	private String username;

	@Column(nullable = false, length = 255)
	private String password;

	@Column(nullable = false, length = 30)
	private String nickname;

	@Column(unique = true)
	private String refreshToken;

	private LocalDateTime refreshTokenExpiration;

	public Users(String username, String password, String nickname) {
		this.username = username;
		this.password = password;
		this.nickname = nickname;
	}

	// 리프레시 토큰 설정
	public void updateRefreshToken(String refreshToken, LocalDateTime expiration) {
		this.refreshToken = refreshToken;
		this.refreshTokenExpiration = expiration;
	}

	// 리프레시 토큰 초기화
	public void clearRefreshToken() {
		this.refreshToken = null;
		this.refreshTokenExpiration = null;
	}

}
