package com.back.together02be.chart.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.back.together02be.chart.constant.ChartPeriod;
import com.back.together02be.chart.dto.response.ChartRes;
import com.back.together02be.chart.dto.response.KisChartApiRes;
import com.back.together02be.infra.kis.rest.KisPriceClient;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChartService 단위 테스트")
class ChartServiceTest {

	// Mock

	@Mock
	private KisPriceClient kisPriceClient;

	// SUT

	@InjectMocks
	private ChartService chartService;

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
	@DisplayName("3M 기간 조회 시 ChartRes 를 반환하고 캔들은 날짜 오름차순으로 정렬된다")
	void 삼개월_기간_정상_조회() {
		KisChartApiRes apiRes = makeApiRes(List.of(
			makeOutput2("20240103"),
			makeOutput2("20240101"),
			makeOutput2("20240102")
		));
		given(kisPriceClient.fetchCandles("005930", ChartPeriod.THREE_MONTHS)).willReturn(apiRes);

		ChartRes result = chartService.getChart("005930", "3M");

		assertThat(result.stockCode()).isEqualTo("005930");
		assertThat(result.name()).isEqualTo("삼성전자");
		assertThat(result.period()).isEqualTo("3M");
		assertThat(result.interval()).isEqualTo("DAY");
		assertThat(result.candles()).hasSize(3);
		assertThat(result.candles())
			.extracting("time")
			.containsExactly("2024-01-01", "2024-01-02", "2024-01-03");
	}

	@Test
	@DisplayName("1Y 기간 조회 시 interval 이 WEEK 이다")
	void 일년_기간_interval_WEEK() {
		KisChartApiRes apiRes = makeApiRes(List.of(makeOutput2("20240101")));
		given(kisPriceClient.fetchCandles("005930", ChartPeriod.ONE_YEAR)).willReturn(apiRes);

		ChartRes result = chartService.getChart("005930", "1Y");

		assertThat(result.interval()).isEqualTo("WEEK");
	}

	@Test
	@DisplayName("지원하지 않는 period 값이면 IllegalArgumentException 이 발생한다")
	void 잘못된_period_예외() {
		assertThatThrownBy(() -> chartService.getChart("005930", "INVALID"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("INVALID");
	}

	@Test
	@DisplayName("KIS API 응답 output2 가 비어있으면 EntityNotFoundException 이 발생한다")
	void 빈_캔들_EntityNotFoundException() {
		KisChartApiRes apiRes = makeApiRes(List.of());
		given(kisPriceClient.fetchCandles("005930", ChartPeriod.THREE_MONTHS)).willReturn(apiRes);

		assertThatThrownBy(() -> chartService.getChart("005930", "3M"))
			.isInstanceOf(EntityNotFoundException.class)
			.hasMessageContaining("005930");
	}
}