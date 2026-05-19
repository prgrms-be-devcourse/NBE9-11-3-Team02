package com.back.together02be.global.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import java.util.Optional

interface IdempotencyKeyRepository : JpaRepository<IdempotencyKey, Long> {

    // TODO Phase 3: Optional → IdempotencyKey? (Nullable 타입으로 리팩토링, GlobalExceptionHandler + 확장 함수 조합)
    fun findByIdempotencyKey(idempotencyKey: String): Optional<IdempotencyKey>

    fun deleteByIdempotencyKey(idempotencyKey: String)

    // 처리 중(responseJson=null)인 채로 오래된 키 정리 (서버 크래시 복구용)
    fun deleteByResponseJsonIsNullAndCreatedAtBefore(dateTime: LocalDateTime): Int

    fun deleteByCreatedAtBefore(dateTime: LocalDateTime)
}
