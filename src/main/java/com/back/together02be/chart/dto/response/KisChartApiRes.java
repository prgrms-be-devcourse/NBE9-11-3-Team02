package com.back.together02be.chart.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisChartApiRes(
	@JsonProperty("rt_cd")  String rtCd, // "0"이면 성공
	@JsonProperty("msg1")   String msg1, // 에러 메시지
	@JsonProperty("output1") Output1 output1,
	@JsonProperty("output2") List<Output2> output2
) {
	public record Output1(
		@JsonProperty("hts_kor_isnm") String htsKorIsnm
	) {}

	public record Output2(
		@JsonProperty("stck_bsop_date") String stckBsopDate,  // 날짜 yyyyMMdd
		@JsonProperty("stck_oprc")      String stckOprc,       // 시가
		@JsonProperty("stck_hgpr")      String stckHgpr,       // 고가
		@JsonProperty("stck_lwpr")      String stckLwpr,       // 저가
		@JsonProperty("stck_clpr")      String stckClpr,       // 종가
		@JsonProperty("acml_vol")       String acmlVol         // 거래량
	) {}
}
