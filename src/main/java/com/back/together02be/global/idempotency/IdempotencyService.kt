package com.back.together02be.global.idempotency

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class IdempotencyService(
    private val idempotencyKeyRepository: IdempotencyKeyRepository,
    private val idempotencyKeyStore: IdempotencyKeyStore,
) {
    private val log = LoggerFactory.getLogger(IdempotencyService::class.java)

    /**
     * UNIQUE 제약을 DB 단에서 원자적으로 검사.
     * IdempotencyKeyStore의 REQUIRES_NEW 트랜잭션 밖에서 DataIntegrityViolationException을 catch →
     * UnexpectedRollbackException 없이 안전하게 중복 감지.
     *
     * @return true  → 처음 요청 (처리 허용)
     *         false → 중복 요청 (차단)
     */
    fun registerIfAbsent(key: String, userId: Long): Boolean {
        return try {
            idempotencyKeyStore.register(key, userId)
            true
        } catch (e: DataIntegrityViolationException) {
            false
        }
    }

    /**
     * 완료된 요청의 캐시된 응답 JSON 조회.
     * responseJson이 null이면 아직 처리 중인 요청.
     */
    @Transactional(readOnly = true)
    fun getStoredResponse(key: String): Optional<String> {
        return idempotencyKeyRepository.findByIdempotencyKey(key)
            .flatMap { Optional.ofNullable(it.responseJson) }
    }

    // 처리 실패 시 키 반납 — 클라이언트 재시도 허용.
    @Transactional
    fun remove(key: String) {
        try {
            idempotencyKeyRepository.deleteByIdempotencyKey(key)
        } catch (e: Exception) {
            log.warn("멱등성 키 삭제 실패: {}", key, e)
        }
    }

    /**
     * 30초마다 실행 — 60초 이상 처리 중(responseJson=null)인 키 삭제.
     * 서버 크래시로 응답 저장 못 한 키를 정리해 재시도를 허용.
     */
    @Scheduled(fixedDelay = 300000)
    @Transactional
    fun cleanupStaleInProgressKeys() {
        val staleThreshold = LocalDateTime.now().minusSeconds(60)
        val deleted = idempotencyKeyRepository.deleteByResponseJsonIsNullAndCreatedAtBefore(staleThreshold)
        if (deleted > 0) {
            log.info("처리 중 상태로 방치된 멱등성 키 {}개 정리", deleted)
        }
    }

    // 매일 자정 — 하루 이상 지난 키 정리.
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    fun cleanupExpiredKeys() {
        val expiry = LocalDateTime.now().minusDays(1)
        idempotencyKeyRepository.deleteByCreatedAtBefore(expiry)
        log.info("만료 멱등성 키 정리 완료")
    }
}
