package com.back.together02be.infra.kis.rest.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "kis_access_token")
class KisAccessToken(
    // 주 생성자에는 값을 받아오는 역할만 담당합니다. (var/val 제거)
    accessToken: String,
    tokenType: String,
    expiresAt: LocalDateTime
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    // 클래스 내부 바디에서 프로퍼티를 선언하고 protected set을 걸어줍니다.
    @Column(nullable = false, length = 2000)
    var accessToken: String = accessToken
        protected set

    @Column(nullable = false, length = 50)
    var tokenType: String = tokenType
        protected set

    @Column(nullable = false)
    var expiresAt: LocalDateTime = expiresAt
        protected set

    fun update(accessToken: String, tokenType: String, expiresAt: LocalDateTime) {
        this.accessToken = accessToken
        this.tokenType = tokenType
        this.expiresAt = expiresAt
    }

    @get:Transient
    val isUsable: Boolean
        get() = expiresAt.isAfter(LocalDateTime.now().plusMinutes(1))
}