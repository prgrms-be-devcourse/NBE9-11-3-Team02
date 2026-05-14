package com.back.together02be.global.idempotency;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

	private final IdempotencyKeyRepository idempotencyKeyRepository;
	private final IdempotencyKeyStore idempotencyKeyStore;

	/**
	 * UNIQUE 제약을 DB 단에서 원자적으로 검사.
	 * IdempotencyKeyStore의 REQUIRES_NEW 트랜잭션 밖에서 DataIntegrityViolationException을 catch →
	 * UnexpectedRollbackException 없이 안전하게 중복 감지.
	 *
	 * @return true  → 처음 요청 (처리 허용)
	 *         false → 중복 요청 (차단)
	 */
	public boolean registerIfAbsent(String key, Long userId) {
		try {
			idempotencyKeyStore.register(key, userId);
			return true;
		} catch (DataIntegrityViolationException e) {
			return false;
		}
	}

	/**
	 * 완료된 요청의 캐시된 응답 JSON 조회.
	 * responseJson이 null이면 아직 처리 중인 요청.
	 */
	@Transactional(readOnly = true)
	public Optional<String> getStoredResponse(String key) {
		return idempotencyKeyRepository.findByIdempotencyKey(key)
				.map(IdempotencyKey::getResponseJson)
				.filter(json -> json != null);
	}

	/**
	 * 처리 실패 시 키 반납 — 클라이언트 재시도 허용.
	 */
	@Transactional
	public void remove(String key) {
		try {
			idempotencyKeyRepository.deleteByIdempotencyKey(key);
		} catch (Exception e) {
			log.warn("멱등성 키 삭제 실패: {}", key, e);
		}
	}

	/**
	 * 30초마다 실행 — 60초 이상 처리 중(responseJson=null)인 키 삭제.
	 * 서버 크래시로 응답 저장 못 한 키를 정리해 재시도를 허용.
	 */
	@Scheduled(fixedDelay = 300000)
	@Transactional
	public void cleanupStaleInProgressKeys() {
		LocalDateTime staleThreshold = LocalDateTime.now().minusSeconds(60);
		int deleted = idempotencyKeyRepository.deleteByResponseJsonIsNullAndCreatedAtBefore(staleThreshold);
		if (deleted > 0) {
			log.info("처리 중 상태로 방치된 멱등성 키 {}개 정리", deleted);
		}
	}

	/**
	 * 매일 자정 — 하루 이상 지난 키 정리.
	 */
	@Scheduled(cron = "0 0 0 * * *")
	@Transactional
	public void cleanupExpiredKeys() {
		LocalDateTime expiry = LocalDateTime.now().minusDays(1);
		idempotencyKeyRepository.deleteByCreatedAtBefore(expiry);
		log.info("만료 멱등성 키 정리 완료");
	}
}
