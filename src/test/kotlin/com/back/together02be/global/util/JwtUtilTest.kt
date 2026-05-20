package com.back.together02be.global.util

import com.back.together02be.global.util.JwtUtil.generateAccessToken
import com.back.together02be.global.util.JwtUtil.isValid
import com.back.together02be.global.util.JwtUtil.payloadOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

internal class JwtUtilTest {
    @Test
    @DisplayName("정상 access token - payload 읽기")
    fun generateAccessToken_andReadPayload() {
        // given
        val body = mapOf(
            "id" to 1L,
            "username" to "testuser",
            "nickname" to "테스터"
        )

        // when
        val token = generateAccessToken(SECRET, 3600L, body)
        val payload = payloadOrNull(token, SECRET)

        // then
        assertThat(token).isNotBlank()
        assertThat(payload).isNotNull()
        val tokenPayload = requireNotNull(payload)
        assertThat((tokenPayload["id"] as Number).toLong()).isEqualTo(1L)
        assertThat(tokenPayload["username"]).isEqualTo("testuser")
        assertThat(tokenPayload["nickname"]).isEqualTo("테스터")
    }

    @Test
    @DisplayName("정상 token 유효성 검증 - 성공")
    fun validToken_returnsTrue() {
        val token = generateAccessToken(
            SECRET,
            3600L,
            mapOf("id" to 1L, "username" to "testuser", "nickname" to "테스터")
        )

        val result = isValid(token, SECRET)

        assertThat(result).isTrue()
    }

    @Test
    @DisplayName("다른 secret 검증 - 실패")
    fun tokenWithDifferentSecret_returnsFalse() {
        val token = generateAccessToken(
            SECRET,
            3600L,
            mapOf("id" to 1L, "username" to "testuser", "nickname" to "테스터")
        )

        val result = isValid(token, OTHER_SECRET)

        assertThat(result).isFalse()
    }

    @Test
    @DisplayName("만료된 token 유효성 검증 - 실패")
    fun expiredToken_returnsFalse() {
        val token = generateAccessToken(
            SECRET,
            -1L,
            mapOf("id" to 1L, "username" to "testuser", "nickname" to "테스터")
        )

        val result = isValid(token, SECRET)

        assertThat(result).isFalse()
    }

    @Test
    @DisplayName("잘못된 token payload - null")
    fun malformedToken_payloadIsNull() {
        val payload = payloadOrNull("invalid.jwt.token", SECRET)

        assertThat(payload).isNull()
    }

    companion object {
        private const val SECRET = "test-secret-key-must-be-32-bytes!!"
        private const val OTHER_SECRET = "other-secret-key-must-be-32-bytes!"
    }
}
