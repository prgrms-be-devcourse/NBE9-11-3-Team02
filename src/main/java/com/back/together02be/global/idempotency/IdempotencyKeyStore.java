package com.back.together02be.global.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Transactional(REQUIRES_NEW) 경계를 별도 빈으로 분리.
 * IdempotencyService에서 DataIntegrityViolationException을 트랜잭션 밖에서 catch할 수 있게 한다.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyKeyStore {

	private final IdempotencyKeyRepository idempotencyKeyRepository;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void register(String key, Long userId) {
		idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(key, userId));
	}
}
