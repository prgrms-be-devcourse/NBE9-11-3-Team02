package com.back.together02be.global.security

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class RefreshTokenCookieManager(
    @Value("\${jwt.refresh-expire-seconds}")
    private val refreshExpireSeconds: Int
) {

    fun addRefreshTokenCookie(response: HttpServletResponse, refreshToken: String) {
        val cookie = createBaseCookie(refreshToken)
        cookie.maxAge = refreshExpireSeconds
        response.addCookie(cookie)
    }

    fun deleteRefreshTokenCookie(response: HttpServletResponse) {
        val cookie = createBaseCookie("")
        cookie.maxAge = 0
        response.addCookie(cookie)
    }

    private fun createBaseCookie(value: String): Cookie {
        return Cookie(COOKIE_NAME, value).apply {
            path = COOKIE_PATH
            isHttpOnly = true
            domain = COOKIE_DOMAIN
            secure = true
            setAttribute("SameSite", SAME_SITE)
        }
    }

    companion object {
        const val COOKIE_NAME = "refreshToken"

        private const val COOKIE_PATH = "/"
        private const val COOKIE_DOMAIN = "localhost"
        private const val SAME_SITE = "Strict"
    }
}
