package com.back.together02be.global.util

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*

object TokenHashUtil {
    fun sha256(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashedToken = digest.digest(token.toByteArray(StandardCharsets.UTF_8))

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(hashedToken)
    }
}
