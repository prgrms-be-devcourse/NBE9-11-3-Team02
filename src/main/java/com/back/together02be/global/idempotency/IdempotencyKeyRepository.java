package com.back.together02be.global.idempotency;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

	Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);

	void deleteByIdempotencyKey(String idempotencyKey);

	// 처리 중(responseJson=null)인 채로 오래된 키 정리 (서버 크래시 복구용)
	int deleteByResponseJsonIsNullAndCreatedAtBefore(LocalDateTime dateTime);

	void deleteByCreatedAtBefore(LocalDateTime dateTime);
}
