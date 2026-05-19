package com.back.together02be.global.idempotency

import com.back.together02be.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity

@Entity
class IdempotencyKey(
    @Column(nullable = false, unique = true, length = 36)
    val idempotencyKey: String,

    @Column(nullable = false)
    val userId: Long,
) : BaseEntity() {

    // null = 처리 중, non-null = 처리 완료 및 응답 캐시됨
    @Column(columnDefinition = "TEXT")
    var responseJson: String? = null

    fun storeResponse(responseJson: String) {
        this.responseJson = responseJson
    }
}
