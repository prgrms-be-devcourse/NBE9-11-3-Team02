package com.back.together02be.chart.dto.response

import com.fasterxml.jackson.annotation.JsonProperty

// todo @JvmRecord 제거
@JvmRecord
data class KisChartApiRes(
	@JsonProperty("rt_cd")   val rtCd: String, // "0"이면 성공
	@JsonProperty("msg1")    val msg1: String, // 에러 메시지
	@JsonProperty("output1") val output1: Output1,
	@JsonProperty("output2") val output2: List<Output2>
) {

	// todo @JvmRecord 제거
	@JvmRecord
    data class Output1(
		@JsonProperty("hts_kor_isnm") val htsKorIsnm: String
    )

	// todo @JvmRecord 제거
	@JvmRecord
    data class Output2(
		@JsonProperty("stck_bsop_date") val stckBsopDate: String, // 날짜 yyyyMMdd
		@JsonProperty("stck_oprc")      val stckOprc: String,     // 시가
		@JsonProperty("stck_hgpr")      val stckHgpr: String,     // 고가
		@JsonProperty("stck_lwpr")      val stckLwpr: String,     // 저가
		@JsonProperty("stck_clpr")      val stckClpr: String,     // 종가
		@JsonProperty("acml_vol")       val acmlVol: String,      // 거래량
    )
}
