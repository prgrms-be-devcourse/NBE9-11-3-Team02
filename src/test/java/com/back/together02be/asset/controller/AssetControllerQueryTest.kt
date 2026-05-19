package com.back.together02be.asset.controller

import com.back.together02be.asset.dto.response.UserStockRes
import com.back.together02be.asset.service.AssetService
import com.back.together02be.global.security.CustomAuthenticationFilter
import com.back.together02be.global.security.SecurityUser
import com.back.together02be.support.ControllerTestSupport
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.`when`
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AssetControllerQueryTest : ControllerTestSupport() {

    // Spring Boot 3.4+ 의 @MockitoBean 적용 및 지연 초기화(lateinit) 사용
    @MockitoBean
    lateinit var assetService: AssetService

    @MockitoBean
    lateinit var jwtAuthFilter: CustomAuthenticationFilter

    @BeforeEach
    fun configureFilter() { // throws Exception 제거됨
        // 가짜 필터체인 무조건 통과하도록 설정
        doAnswer { inv ->
            val request = inv.arguments[0] as ServletRequest
            val response = inv.arguments[1] as ServletResponse
            val chain = inv.arguments[2] as FilterChain

            chain.doFilter(request, response)
            null
        }.`when`(jwtAuthFilter).doFilter(any(), any(), any())
    }

    // 단일 표현식 함수(Single-Expression Function)를 사용하여 간결하게 가짜 유저 생성
    private fun mockUser() = SecurityUser(
        id = 1L,
        username = "testuser",
        password = "password",
        nickname = "테스터",
        authorities = listOf()
    )

    @Test
    @DisplayName("보유 종목 조회 정상 요청 — 200 OK 및 JSON 규격 검증")
    fun getUserStocks_ReturnsOk() { // throws Exception 제거됨
        // given: Service 응답 Mocking
        val mockRes = UserStockRes("005930", "삼성전자", 10L, 50000L, 75000L)
        `when`(assetService.getUserStocks(anyLong())).thenReturn(listOf(mockRes))

        // when & then
        mockMvc.perform(
            get("/api/asset/stocks")
                .with(user(mockUser()))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].stockCode").value("005930"))
            .andExpect(jsonPath("$.data[0].quantity").value(10))
            .andExpect(jsonPath("$.data[0].currentPrice").value(75000))
    }
}