package com.back.together02be.chart.service

import com.back.together02be.chart.constant.ChartPeriod
import com.back.together02be.chart.dto.response.KisChartApiRes
import com.back.together02be.chart.dto.response.KisChartApiRes.Output1
import com.back.together02be.chart.dto.response.KisChartApiRes.Output2
import com.back.together02be.infra.kis.rest.KisPriceClient
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
@DisplayName("ChartService 단위 테스트")
internal class ChartServiceTest {

    // Mock
    @Mock
    private lateinit var kisPriceClient: KisPriceClient

    // SUT
    @InjectMocks
    private lateinit var chartService: ChartService

    // 헬퍼

    private fun makeApiRes(output2: List<Output2>) =
        KisChartApiRes(
            rtCd = "0",
            msg1 = null,
            output1 = Output1("삼성전자"),
            output2 = output2,
        )

    private fun makeOutput2(date: String) =
        Output2(
            stckBsopDate = date,
            stckOprc = "70000",
            stckHgpr = "71000",
            stckLwpr = "69000",
            stckClpr = "70500",
            acmlVol = "1000000",
        )

    @Test
    @DisplayName("3M 기간 조회 시 ChartRes 를 반환하고 캔들은 날짜 오름차순으로 정렬된다")
    fun 삼개월_기간_정상_조회() {
        val apiRes = makeApiRes(listOf(
            makeOutput2("20260103"),
            makeOutput2("20260101"),
            makeOutput2("20260102"),
        ))
        given(kisPriceClient.fetchCandles("005930", ChartPeriod.THREE_MONTHS)).willReturn(apiRes)

        val result = chartService.getChart("005930", "3M")

        assertThat(result.stockCode).isEqualTo("005930")
        assertThat(result.name).isEqualTo("삼성전자")
        assertThat(result.period).isEqualTo("3M")
        assertThat(result.interval).isEqualTo("DAY")
        assertThat(result.candles).hasSize(3)
        assertThat(result.candles)
            .extracting("time")
            .containsExactly("2026-01-01", "2026-01-02", "2026-01-03")
    }

    @Test
    @DisplayName("1Y 기간 조회 시 interval 이 WEEK 이다")
    fun `일년 기간 interval WEEK`() {
        val apiRes = makeApiRes(listOf(makeOutput2("20240101")))
        given(kisPriceClient.fetchCandles("005930", ChartPeriod.ONE_YEAR)).willReturn(apiRes)

        val result = chartService.getChart("005930", "1Y")

        assertThat(result.interval).isEqualTo("WEEK")
    }

    @Test
    @DisplayName("지원하지 않는 period 값이면 IllegalArgumentException 이 발생한다")
    fun `잘못된 period 예외`() {
        assertThatThrownBy { chartService.getChart("005930", "INVALID") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INVALID")
    }

    @Test
    @DisplayName("KIS API 응답 output2 가 비어있으면 EntityNotFoundException 이 발생한다")
    fun `빈 캔들 EntityNotFoundException`() {
        val apiRes = makeApiRes(emptyList())
        given(kisPriceClient.fetchCandles("005930", ChartPeriod.THREE_MONTHS)).willReturn(apiRes)

        assertThatThrownBy { chartService.getChart("005930", "3M") }
            .isInstanceOf(EntityNotFoundException::class.java)
            .hasMessageContaining("005930")
    }

}