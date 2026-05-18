package com.back.together02be.infra.kis.rest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.back.together02be.infra.kis.rest.entity.KisAccessToken;
import com.back.together02be.infra.kis.rest.repository.KisAccessTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

@ExtendWith(MockitoExtension.class)
class KisTokenServiceTest {

    private KisTokenService kisTokenService;

    @Mock
    private KisAccessTokenRepository kisAccessTokenRepository;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient mockRestClient = builder.build();

        kisTokenService = new KisTokenService(
                mockRestClient,
                kisAccessTokenRepository,
                "test-app-key",
                "test-app-secret",
                "https://api.kis.com"
        );
    }

    @Test
    @DisplayName("유효한 토큰이 있으면 API 통신 없이 기존 토큰을 반환한다")
    void return_existing_token() {
        KisAccessToken validToken = new KisAccessToken("valid-token", "Bearer", LocalDateTime.now().plusHours(1));
        when(kisAccessTokenRepository.findTopByOrderByIdDesc()).thenReturn(validToken); // Optional 제거됨

        String token = kisTokenService.getAccessToken();

        assertThat(token).isEqualTo("valid-token");
    }

    @Test
    @DisplayName("유효한 토큰이 없으면 KIS API를 호출하여 신규 토큰을 발급받고 DB에 저장한다")
    void issue_new_token() {
        when(kisAccessTokenRepository.findTopByOrderByIdDesc()).thenReturn(null); // Optional.empty() 대신 null 리턴

        String mockResponse = """
                {
                  "access_token": "new-fresh-token",
                  "token_type": "Bearer",
                  "expires_in": 86400
                }
                """;

        mockServer.expect(requestTo("https://api.kis.com/oauth2/tokenP"))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        String token = kisTokenService.getAccessToken();

        assertThat(token).isEqualTo("new-fresh-token");
        verify(kisAccessTokenRepository, times(1)).save(any(KisAccessToken.class));
    }
}