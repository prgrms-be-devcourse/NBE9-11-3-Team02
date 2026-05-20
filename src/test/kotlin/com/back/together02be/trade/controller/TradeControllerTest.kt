package com.back.together02be.trade.controller

import com.back.together02be.global.exception.DuplicateRequestException
import com.back.together02be.global.security.CustomAuthenticationFilter
import com.back.together02be.global.security.SecurityUser
import com.back.together02be.support.ControllerTestSupport
import com.back.together02be.trade.dto.BuyRes
import com.back.together02be.trade.service.TradeService
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Mockito.doAnswer
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// K2 컴파일러가 Kotlin 함수의 non-null 파라미터에 any() null을 넘길 때 call site에서 NPE 발생.
// unchecked cast로 null을 T로 위장해 K2 null-check를 우회. Mockito 매처 동작은 그대로.
@Suppress("UNCHECKED_CAST")
private fun <T> anyArg(): T = Mockito.any<T>() as T

class TradeControllerTest : ControllerTestSupport() {

    @MockitoBean
    lateinit var tradeService: TradeService

    @MockitoBean
    lateinit var jwtAuthFilter: CustomAuthenticationFilter

    @BeforeEach
    fun configureFilter() {
        // mock 필터가 chain을 통과하도록 설정 (기본 mock은 doFilter를 막아서 DispatcherServlet에 못 도달)
        doAnswer { inv ->
            inv.getArgument<FilterChain>(2)
                .doFilter(inv.getArgument(0), inv.getArgument(1))
            null
        }.`when`(jwtAuthFilter).doFilter(Mockito.any(), Mockito.any(), Mockito.any())
    }

    private fun mockUser() = SecurityUser(1L, "testuser", "password", "테스터", emptyList())

    @Test
    @DisplayName("정상 매수 요청 — 200 OK")
    fun 정상_매수_요청() {
        val response = BuyRes(1L, "삼성전자", 10L, 70_000L, 700_000L, 49_300_000L)
        Mockito.`when`(tradeService.buy(anyLong(), anyString(), anyArg())).thenReturn(response)

        mockMvc.perform(
            post("/api/trades/buy")
                .with(user(mockUser()))
                .header("X-Idempotency-Key", "test-uuid-1234")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "stockId": 1,
                      "quantity": 10,
                      "expectedPrice": 70000
                    }
                """.trimIndent())
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("매수가 완료되었습니다."))
            .andExpect(jsonPath("$.data.tradeId").value(1))
            .andExpect(jsonPath("$.data.stockName").value("삼성전자"))
            .andExpect(jsonPath("$.data.quantity").value(10))
            .andExpect(jsonPath("$.data.price").value(70_000))
            .andExpect(jsonPath("$.data.amount").value(700_000))
            .andExpect(jsonPath("$.data.remainingDeposit").value(49_300_000))
    }

    @Test
    @DisplayName("stockId null — 400 + 검증 메시지")
    fun stockId_null_검증_실패() {
        mockMvc.perform(
            post("/api/trades/buy")
                .with(user(mockUser()))
                .header("X-Idempotency-Key", "test-uuid-1234")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "stockId": null,
                      "quantity": 10,
                      "expectedPrice": 70000
                    }
                """.trimIndent())
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("종목 ID는 필수입니다."))
    }

    @Test
    @DisplayName("quantity null — 400 + 검증 메시지")
    fun quantity_null_검증_실패() {
        mockMvc.perform(
            post("/api/trades/buy")
                .with(user(mockUser()))
                .header("X-Idempotency-Key", "test-uuid-1234")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "stockId": 1,
                      "quantity": null,
                      "expectedPrice": 70000
                    }
                """.trimIndent())
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("매수 수량은 필수입니다."))
    }

    @Test
    @DisplayName("quantity 0 — 400 + 검증 메시지")
    fun quantity_0_검증_실패() {
        mockMvc.perform(
            post("/api/trades/buy")
                .with(user(mockUser()))
                .header("X-Idempotency-Key", "test-uuid-1234")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "stockId": 1,
                      "quantity": 0,
                      "expectedPrice": 70000
                    }
                """.trimIndent())
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("매수 수량은 1 이상이어야 합니다."))
    }

    @Test
    @DisplayName("expectedPrice null — 400 + 검증 메시지")
    fun expectedPrice_null_검증_실패() {
        mockMvc.perform(
            post("/api/trades/buy")
                .with(user(mockUser()))
                .header("X-Idempotency-Key", "test-uuid-1234")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "stockId": 1,
                      "quantity": 10,
                      "expectedPrice": null
                    }
                """.trimIndent())
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("예상 체결가는 필수입니다."))
    }

    @Test
    @DisplayName("중복 요청 — 409 + 멱등성 메시지")
    fun 중복_요청_차단() {
        Mockito.`when`(tradeService.buy(anyLong(), anyString(), anyArg()))
            .thenThrow(DuplicateRequestException("이미 처리된 요청입니다."))

        mockMvc.perform(
            post("/api/trades/buy")
                .with(user(mockUser()))
                .header("X-Idempotency-Key", "duplicate-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "stockId": 1,
                      "quantity": 10,
                      "expectedPrice": 70000
                    }
                """.trimIndent())
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("이미 처리된 요청입니다."))
    }
}
