package com.back.together02be.infra.kis.rest.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class KisPriceRes(
    @JsonProperty("rt_cd")
    val returnCode: String,    // API 응답 코드

    @JsonProperty("msg_cd")
    val messageCode: String,   // API 메시지 코드

    @JsonProperty("msg1")
    val message: String,       // API 응답 메시지

    val output: Output        // (이건 객체 이름 그대로라 냅둬도 됩니다)
) {
    data class Output(
        @JsonProperty("stck_prpr")
        val currentPrice: String,     // 현재가

        @JsonProperty("prdy_vrss")
        val priceDifference: String,  // 전일 대비 금액

        @JsonProperty("prdy_vrss_sign")
        val changeSign: String,       // 전일 대비 부호

        @JsonProperty("prdy_ctrt")
        val changeRate: String        // 전일 대비 등락률
    )
}