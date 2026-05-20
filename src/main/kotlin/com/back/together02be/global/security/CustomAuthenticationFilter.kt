package com.back.together02be.global.security

import com.back.together02be.global.util.JwtUtil
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

@Component
class CustomAuthenticationFilter(
    @param:Value("\${jwt.secret}") private val jwtSecret: String
) : OncePerRequestFilter() {
    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        var token: String? = null

        // 1. 헤더에서 토큰 추출 시도
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7) // "Bearer " 이후의 순수 토큰만 추출
        } else {
            token = request.getParameter("token") // 파라미터는 "Bearer "가 없으므로 그대로 사용
        }

        // 3. 둘 다 없으면 검증 없이 통과 (SecurityConfig의 인가 설정에 맡김)
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        val payload = JwtUtil.payloadOrNull(token, jwtSecret)

        // JWT 검증 후 유효하면 Security Context에 저장
        if (payload != null) {
            val idClaim = payload["id"]
            val username = payload["username"] as? String
            val nickname = payload["nickname"] as? String

            if (idClaim is Number && username != null && nickname != null) {
                val userDetails = SecurityUser(
                    idClaim.toLong(),
                    username,
                    "",
                    nickname,
                    mutableListOf<GrantedAuthority>()
                )

                val authentication = UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.authorities
                )

                SecurityContextHolder.getContext().setAuthentication(authentication)
            }
        }

        filterChain.doFilter(request, response)
    }
}
