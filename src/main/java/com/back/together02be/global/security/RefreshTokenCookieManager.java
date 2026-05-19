package com.back.together02be.global.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieManager {

    public static final String COOKIE_NAME = "refreshToken";

    private static final String COOKIE_PATH = "/";
    private static final String COOKIE_DOMAIN = "localhost";
    private static final String SAME_SITE = "Strict";

    private final int refreshExpireSeconds;

    public RefreshTokenCookieManager(@Value("${jwt.refresh-expire-seconds}") int refreshExpireSeconds) {
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    public void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = createBaseCookie(refreshToken);
        cookie.setMaxAge(refreshExpireSeconds);
        response.addCookie(cookie);
    }

    public void deleteRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = createBaseCookie("");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private Cookie createBaseCookie(String value) {
        Cookie cookie = new Cookie(COOKIE_NAME, value);
        cookie.setPath(COOKIE_PATH);
        cookie.setHttpOnly(true);
        cookie.setDomain(COOKIE_DOMAIN);
        cookie.setSecure(true);
        cookie.setAttribute("SameSite", SAME_SITE);
        return cookie;
    }
}
