package com.back.together02be.global.idempotency;

import com.back.together02be.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class IdempotencyKey extends BaseEntity {

	@Column(nullable = false, unique = true, length = 36)
	private String idempotencyKey;

	@Column(nullable = false)
	private Long userId;

	// null = 처리 중, non-null = 처리 완료 및 응답 캐시됨
	@Column(columnDefinition = "TEXT")
	private String responseJson;

	public IdempotencyKey(String idempotencyKey, Long userId) {
		this.idempotencyKey = idempotencyKey;
		this.userId = userId;
	}

	public void storeResponse(String responseJson) {
		this.responseJson = responseJson;
	}
}
