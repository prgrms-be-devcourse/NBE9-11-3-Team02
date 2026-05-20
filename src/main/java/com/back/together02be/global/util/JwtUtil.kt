package com.back.together02be.global.util

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.nio.charset.StandardCharsets
import java.security.Key
import java.util.*

object JwtUtil {
    fun generateAccessToken(
        secret: String,
        expireSeconds: Long,
        body: Map<String, Any?>
    ): String {
        val claimsBuilder = Jwts.claims()

        body.forEach { (key, value) -> claimsBuilder.add(key, value) }

        val claims = claimsBuilder.build()

        val issuedAt = Date()
        val expiration = Date(issuedAt.time + 1000L * expireSeconds)

        val secretKey: Key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

        return Jwts.builder()
            .claims(claims)
            .issuedAt(issuedAt)
            .expiration(expiration)
            .signWith(secretKey)
            .compact()
    }

    fun isValid(token: String?, secret: String): Boolean {
        val secretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parse(token)
                .getPayload()

            return true
        } catch (e: Exception) {
            return false
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun payloadOrNull(token: String?, secret: String): Map<String, Any>? {
        val secretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

        if (isValid(token, secret)) {
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parse(token)
                .payload as Map<String, Any>
        }

        return null
    }
}
