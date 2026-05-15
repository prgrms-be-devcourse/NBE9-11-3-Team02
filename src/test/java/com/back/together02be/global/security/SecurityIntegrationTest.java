package com.back.together02be.global.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.back.together02be.global.util.JwtUtil;
import com.back.together02be.support.ControllerTestSupport;

@TestPropertySource(properties = {
    "jwt.secret=test-secret-key-must-be-32-bytes!!"
})
@Import(SecurityIntegrationTest.TestProtectedController.class)
class SecurityIntegrationTest extends ControllerTestSupport {

    private static final String SECRET = "test-secret-key-must-be-32-bytes!!";

    @Test
    @DisplayName("permitAll API - token 없이 Security 차단 X")
    void permitAllApi_withoutToken_isNotBlockedBySecurity() throws Exception {
//         /api/users/login은 SecurityConfig에서 permitAll이다.
//         body가 없어서 MVC 단계에서 400이 나올 수는 있지만 Security에서 막힌 401이면 안 된다.
        int status = mockMvc.perform(post("/api/users/login"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isNotEqualTo(401);
    }

    @Test
    @DisplayName("보호 API - token 없으면 401")
    void protectedApi_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/test/security/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인 후 이용해주세요."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("보호 API - 잘못된 token이면 401")
    void protectedApi_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(
                        get("/test/security/protected")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인 후 이용해주세요."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("보호 API - JWT가 있으면 접근 가능하다")
    void protectedApi_withValidToken_returns200() throws Exception {
        String token = JwtUtil.generateAccessToken(
                SECRET,
                3600L,
                Map.of(
                        "id", 1L,
                        "username", "testuser",
                        "nickname", "테스터"
                )
        );

        mockMvc.perform(
                        get("/test/security/protected")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.nickname").value("테스터"));
    }

    @Test
    @DisplayName("logout API - token 없으면 401")
    void logout_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/users/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인 후 이용해주세요."))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @RestController
    static class TestProtectedController {

        @GetMapping("/test/security/protected")
        Map<String, Object> protectedEndpoint(@AuthenticationPrincipal SecurityUser user) {
            return Map.of(
                    "userId", user.getId(),
                    "username", user.getUsername(),
                    "nickname", user.getNickname()
            );
        }
    }
}