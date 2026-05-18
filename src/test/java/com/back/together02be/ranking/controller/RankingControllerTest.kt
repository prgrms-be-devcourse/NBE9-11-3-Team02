package com.back.together02be.ranking.controller

import com.back.together02be.ranking.dto.response.RankingRes
import com.back.together02be.ranking.service.RankingSeasonService
import com.back.together02be.ranking.service.RankingService
import com.back.together02be.ranking.service.RankingSnapshotService
import com.back.together02be.support.ControllerTestSupport
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RankingController - 랭킹 API 통합 테스트")
internal class RankingControllerTest : ControllerTestSupport() {

    // Kotlin 전환 포인트: ? = null 대신 lateinit var를 사용하여 Null 단언(!!)을 제거합니다.
    @MockitoBean
    private lateinit var rankingService: RankingService

    @MockitoBean
    private lateinit var rankingSnapshotService: RankingSnapshotService

    @MockitoBean
    private lateinit var rankingSeasonService: RankingSeasonService

    @Test
    @DisplayName("GET /api/rankings - 일간 랭킹을 200 OK와 함께 반환한다")
    fun 일간_랭킹_조회_REST() {
        // given
        // Kotlin 전환 포인트: List.of() 대신 코틀린 내장 함수인 listOf() 사용
        val response = listOf(
            RankingRes(1L, "투자왕", 1, BigDecimal("12.34"), 56170000L)
        )
        given(rankingService.getDailyRankings()).willReturn(response)

        // when & then
        mockMvc.perform(get("/api/rankings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].nickname").value("투자왕"))
            .andExpect(jsonPath("$[0].rank").value(1))

        verify(rankingService).getDailyRankings()
    }

    @Test
    @DisplayName("POST /api/rankings/snapshots/daily - 특정 날짜로 DAILY 스냅샷 생성을 트리거한다")
    fun DAILY_랭킹_스냅샷_수동생성() {
        // given
        val snapshotDate = LocalDate.of(2026, 5, 14)

        // when & then
        mockMvc.perform(
            post("/api/rankings/snapshots/daily")
                .param("snapshotDate", snapshotDate.toString())
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("DAILY 랭킹 생성 완료")))

        verify(rankingSnapshotService).createDailySnapshot(snapshotDate)
    }
}