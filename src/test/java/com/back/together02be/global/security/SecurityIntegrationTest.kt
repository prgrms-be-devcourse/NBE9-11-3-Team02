package com.back.together02be.global.security

import com.back.together02be.global.security.SecurityIntegrationTest.TestProtectedController
import com.back.together02be.global.util.JwtUtil.generateAccessToken
import com.back.together02be.support.ControllerTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@TestPropertySource(
    properties = ["jwt.secret=test-secret-key-must-be-32-bytes!!"
    ]
)
@Import(TestProtectedController::class)
internal class SecurityIntegrationTest : ControllerTestSupport() {
    @Test
    @DisplayName("permitAll API - token 없이 Security 차단 X")
    @Throws(Exception::class)
    fun permitAllApi_withoutToken_isNotBlockedBySecurity() {
//         /api/users/login은 SecurityConfig에서 permitAll이다.
//         body가 없어서 MVC 단계에서 400이 나올 수는 있지만 Security에서 막힌 401이면 안 된다.
        val status = mockMvc.perform(MockMvcRequestBuilders.post("/api/users/login"))
            .andReturn()
            .response
            .status

        assertThat(status).isNotEqualTo(401)
    }

    @Test
    @DisplayName("보호 API - token 없으면 401")
    @Throws(Exception::class)
    fun protectedApi_withoutToken_returns401() {
        mockMvc.perform(MockMvcRequestBuilders.get("/test/security/protected"))
            .andExpect(MockMvcResultMatchers.status().isUnauthorized())
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("로그인 후 이용해주세요."))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data").isEmpty())
    }

    @Test
    @DisplayName("보호 API - 잘못된 token이면 401")
    @Throws(Exception::class)
    fun protectedApi_withInvalidToken_returns401() {
        mockMvc.perform(
            MockMvcRequestBuilders.get("/test/security/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
        )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized())
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("로그인 후 이용해주세요."))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data").isEmpty())
    }

    @Test
    @DisplayName("보호 API - JWT가 있으면 접근 가능하다")
    @Throws(Exception::class)
    fun protectedApi_withValidToken_returns200() {
        val token = generateAccessToken(
            SECRET,
            3600L,
            mapOf(
                "id" to 1L,
                "username" to "testuser",
                "nickname" to "테스터"
            )
        )

        mockMvc.perform(
            MockMvcRequestBuilders.get("/test/security/protected")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        )
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.userId").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.username").value("testuser"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.nickname").value("테스터"))
    }

    @Test
    @DisplayName("logout API - token 없으면 401")
    @Throws(Exception::class)
    fun logout_withoutToken_returns401() {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/users/logout"))
            .andExpect(MockMvcResultMatchers.status().isUnauthorized())
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("로그인 후 이용해주세요."))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data").isEmpty())
    }

    @RestController
    internal class TestProtectedController {
        @GetMapping("/test/security/protected")
        fun protectedEndpoint(@AuthenticationPrincipal user: SecurityUser): Map<String, Any> {
            return mapOf(
                "userId" to user.id,
                "username" to user.username,
                "nickname" to user.nickname
            )
        }
    }

    companion object {
        private const val SECRET = "test-secret-key-must-be-32-bytes!!"
    }
}
