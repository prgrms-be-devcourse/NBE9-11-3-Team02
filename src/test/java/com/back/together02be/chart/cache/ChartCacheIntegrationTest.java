package com.back.together02be.chart.cache;

import static org.mockito.BDDMockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.back.together02be.chart.constant.ChartPeriod;
import com.back.together02be.chart.dto.response.KisChartApiRes;
import com.back.together02be.chart.service.ChartService;
import com.back.together02be.infra.kis.rest.KisPriceClient;
import com.back.together02be.support.IntegrationTestSupport;

@DisplayName("ChartService 캐시 통합 테스트")
class ChartCacheIntegrationTest extends IntegrationTestSupport {

	// Mock
	@MockitoBean
	private KisPriceClient kisPriceClient;

	@Autowired
	private ChartService chartService;

	@Autowired
	private CacheManager cacheManager;

	@BeforeEach
	void clearCache() {
		cacheManager.getCache("chart").clear();
	}

	// 헬퍼

	private static KisChartApiRes makeApiRes(List<KisChartApiRes.Output2> output2) {
		return new KisChartApiRes(
			"0", null,
			new KisChartApiRes.Output1("삼성전자"),
			output2
		);
	}

	private static KisChartApiRes.Output2 makeOutput2(String date) {
		return new KisChartApiRes.Output2(date, "70000", "71000", "69000", "70500", "1000000");
	}

	@Test
	@DisplayName("동일 파라미터로 두 번 호출 시 KisPriceClient 는 1회만 호출된다")
	void 캐시_히트_KIS_API_미호출() {
		KisChartApiRes apiRes = makeApiRes(List.of(makeOutput2("20240101")));
		given(kisPriceClient.fetchCandles("005930", ChartPeriod.THREE_MONTHS)).willReturn(apiRes);

		chartService.getChart("005930", "3M"); // 캐시 미스 → KIS 호출
		chartService.getChart("005930", "3M"); // 캐시 히트 → KIS 미호출

		verify(kisPriceClient, times(1)).fetchCandles("005930", ChartPeriod.THREE_MONTHS);
	}
}