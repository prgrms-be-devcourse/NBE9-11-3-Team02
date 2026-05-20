package com.back.together02be.chart.cache

import com.back.together02be.chart.constant.ChartPeriod
import com.back.together02be.chart.dto.response.KisChartApiRes
import com.back.together02be.chart.dto.response.KisChartApiRes.Output1
import com.back.together02be.chart.dto.response.KisChartApiRes.Output2
import com.back.together02be.chart.service.ChartService
import com.back.together02be.infra.kis.rest.KisPriceClient
import com.back.together02be.support.IntegrationTestSupport
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.test.context.bean.override.mockito.MockitoBean

@DisplayName("ChartService 캐시 통합 테스트")
internal class ChartCacheIntegrationTest : IntegrationTestSupport() {
    // Mock
    @MockitoBean
    private lateinit var kisPriceClient: KisPriceClient

    @Autowired
    private lateinit var chartService: ChartService

    @Autowired
    private lateinit var cacheManager: CacheManager

    @BeforeEach
    fun clearCache() {
        cacheManager.getCache("chart")!!.clear()
    }

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
    @DisplayName("동일 파라미터로 두 번 호출 시 KisPriceClient 는 1회만 호출된다")
    fun 캐시_히트_KIS_API_미호출() {
        val apiRes = makeApiRes(listOf(makeOutput2("20240101")))
        given(kisPriceClient.fetchCandles("005930", ChartPeriod.THREE_MONTHS)).willReturn(apiRes)

        chartService.getChart("005930", "3M") // 캐시 미스 → KIS 호출
        chartService.getChart("005930", "3M") // 캐시 히트 → KIS 미호출

        verify(kisPriceClient, times(1)).fetchCandles("005930", ChartPeriod.THREE_MONTHS)
    }

}