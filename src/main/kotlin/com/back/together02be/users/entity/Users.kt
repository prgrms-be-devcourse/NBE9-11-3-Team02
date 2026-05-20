package com.back.together02be.users.entity

import com.back.together02be.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import java.time.LocalDateTime

@Entity
open class Users(
    @field:Column(nullable = false, unique = true, length = 30)
    val username: String,

    @field:Column(nullable = false, length = 255)
    val password: String,

    @field:Column(nullable = false, length = 30)
    val nickname: String
) : BaseEntity() {

    @field:Column(unique = true)
    var refreshToken: String? = null

    var refreshTokenExpiration: LocalDateTime? = null

    // 리프레시 토큰 설정
    fun updateRefreshToken(refreshToken: String, expiration: LocalDateTime) {
        this.refreshToken = refreshToken
        this.refreshTokenExpiration = expiration
    }

    // 리프레시 토큰 초기화
    fun clearRefreshToken() {
        this.refreshToken = null
        this.refreshTokenExpiration = null
    }
}
