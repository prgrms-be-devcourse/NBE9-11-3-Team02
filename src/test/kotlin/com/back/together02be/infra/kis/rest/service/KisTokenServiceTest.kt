package com.back.together02be.infra.kis.rest.service

import com.back.together02be.infra.kis.rest.entity.KisAccessToken
import com.back.together02be.infra.kis.rest.repository.KisAccessTokenRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class KisTokenServiceTest {

    private lateinit var kisTokenService: KisTokenService

    @Mock
    private lateinit var kisAccessTokenRepository: KisAccessTokenRepository

    private lateinit var mockServer: MockRestServiceServer

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        mockServer = MockRestServiceServer.bindTo(builder).build()
        val mockRestClient = builder.build()

        kisTokenService = KisTokenService(
            mockRestClient,
            kisAccessTokenRepository,
            "test-app-key",
            "test-app-secret",
            "https://api.kis.com"
        )
    }

    @Test
    @DisplayName("유효한 토큰이 있으면 API 통신 없이 기존 토큰을 반환한다")
    fun return_existing_token() {
        val validToken = KisAccessToken("valid-token", "Bearer", LocalDateTime.now().plusHours(1))

        given(kisAccessTokenRepository.findTopByOrderByIdDesc()).willReturn(validToken)

        val token = kisTokenService.getAccessToken()

        assertThat(token).isEqualTo("valid-token")
    }

    @Test
    @DisplayName("유효한 토큰이 없으면 KIS API를 호출하여 신규 토큰을 발급받고 DB에 저장한다")
    fun issue_new_token() {
        given(kisAccessTokenRepository.findTopByOrderByIdDesc()).willReturn(null)

        val mockResponse = """
            {
              "access_token": "new-fresh-token",
              "token_type": "Bearer",
              "expires_in": 86400
            }
        """.trimIndent()

        mockServer.expect(requestTo("https://api.kis.com/oauth2/tokenP"))
            .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON))

        val token = kisTokenService.getAccessToken()

        assertThat(token).isEqualTo("new-fresh-token")
        verify(kisAccessTokenRepository, times(1)).save(any(KisAccessToken::class.java))
    }
}