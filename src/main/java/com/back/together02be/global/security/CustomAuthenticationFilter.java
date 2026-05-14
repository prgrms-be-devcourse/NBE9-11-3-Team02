package com.back.together02be.global.security;

import com.back.together02be.global.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = null;

        // 1. 헤더에서 토큰 추출 시도
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7); // "Bearer " 이후의 순수 토큰만 추출
        }
        // 2. 헤더에 없다면 URL 파라미터에서 추출 시도
        else {
            token = request.getParameter("token"); // 파라미터는 "Bearer "가 없으므로 그대로 사용
        }

        // 3. 둘 다 없으면 검증 없이 통과 (SecurityConfig의 인가 설정에 맡김)
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        Map<String, Object> payload = JwtUtil.payloadOrNull(token, jwtSecret);

        // JWT 검증 후 유효하면 Security Context에 저장
        if (payload != null) {
            Object idClaim = payload.get("id");
            String username = (String) payload.get("username");
            String nickname = (String) payload.get("nickname");

            if (idClaim instanceof Number id && username != null && nickname != null) {
                SecurityUser userDetails = new SecurityUser(
                        id.longValue(),
                        username,
                        "",
                        nickname,
                        List.of()
                );

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
