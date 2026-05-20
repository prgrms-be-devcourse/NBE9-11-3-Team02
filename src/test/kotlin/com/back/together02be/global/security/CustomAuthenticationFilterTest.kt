package com.back.together02be.global.security

import com.back.together02be.global.util.JwtUtil.generateAccessToken
import jakarta.servlet.ServletException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.io.IOException

internal class CustomAuthenticationFilterTest {
    private lateinit var filter: CustomAuthenticationFilter

    @BeforeEach
    fun setUp() {
        filter = CustomAuthenticationFilter(SECRET)
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    @DisplayName("정상 Bearer token이면 SecurityContext에 인증 정보가 저장된다")
    @Throws(ServletException::class, IOException::class)
    fun validBearerToken_setsAuthentication() {
        // given
        val token = generateAccessToken(
            SECRET,
            3600L,
            mapOf(
                "id" to 1L,
                "username" to "testuser",
                "nickname" to "테스터"
            )
        )

        val request = MockHttpServletRequest()
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)

        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // when
        filter.doFilter(request, response, chain)

        // then
        val authentication = SecurityContextHolder.getContext().authentication

        assertThat(authentication).isNotNull()
        val savedAuthentication = requireNotNull(authentication)
        assertThat(savedAuthentication.principal).isInstanceOf(SecurityUser::class.java)

        val principal = savedAuthentication.principal as SecurityUser

        assertThat(principal.id).isEqualTo(1L)
        assertThat(principal.username).isEqualTo("testuser")
        assertThat(principal.nickname).isEqualTo("테스터")
    }

    @Test
    @DisplayName("token없으면 인증 정보 저장 안하고 다음 필터로 패스")
    @Throws(ServletException::class, IOException::class)
    fun noToken_doesNotSetAuthentication() {
        // given
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // when
        filter.doFilter(request, response, chain)

        // then
        val authentication = SecurityContextHolder.getContext().authentication

        assertThat(authentication).isNull()
    }

    @Test
    @DisplayName("잘못된 Bearer token이면 인증 정보를 저장 안함")
    @Throws(ServletException::class, IOException::class)
    fun invalidBearerToken_doesNotSetAuthentication() {
        // given
        val request = MockHttpServletRequest()
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")

        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // when
        filter.doFilter(request, response, chain)

        // then
        val authentication = SecurityContextHolder.getContext().authentication

        assertThat(authentication).isNull()
    }

    @Test
    @DisplayName("Bearer prefix가 아니면 Authorization 헤더를 token으로 사용 안함")
    @Throws(ServletException::class, IOException::class)
    fun nonBearerHeader_doesNotSetAuthentication() {
        // given
        val token = generateAccessToken(
            SECRET,
            3600L,
            mapOf("id" to 1L, "username" to "testuser", "nickname" to "테스터")
        )

        val request = MockHttpServletRequest()
        request.addHeader(HttpHeaders.AUTHORIZATION, token)

        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // when
        filter.doFilter(request, response, chain)

        // then
        val authentication = SecurityContextHolder.getContext().authentication

        assertThat(authentication).isNull()
    }

    @Test
    @DisplayName("URL parameter token으로도 인증 정보가 저장")
    @Throws(ServletException::class, IOException::class)
    fun queryParameterToken_setsAuthentication() {
        // given
        val token = generateAccessToken(
            SECRET,
            3600L,
            mapOf(
                "id" to 1L,
                "username" to "testuser",
                "nickname" to "테스터"
            )
        )

        val request = MockHttpServletRequest()
        request.setParameter("token", token)

        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // when
        filter.doFilter(request, response, chain)

        // then
        val authentication = SecurityContextHolder.getContext().authentication

        assertThat(authentication).isNotNull()
        assertThat(requireNotNull(authentication).principal).isInstanceOf(SecurityUser::class.java)
    }

    @Test
    @DisplayName("payload에 필수 claim 없으면 인증 정보 저장암함")
    @Throws(ServletException::class, IOException::class)
    fun missingRequiredClaims_doesNotSetAuthentication() {
        // given
        val token = generateAccessToken(
            SECRET,
            3600L,
            mapOf(
                "id" to 1L,
                "username" to "testuser"
            )
        )

        val request = MockHttpServletRequest()
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)

        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        // when
        filter.doFilter(request, response, chain)

        // then
        val authentication = SecurityContextHolder.getContext().authentication

        assertThat(authentication).isNull()
    }

    companion object {
        private const val SECRET = "test-secret-key-must-be-32-bytes!!"
    }
}
