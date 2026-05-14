package com.back.together02be.infra.kis.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// 한투 현재가 조회 API의 전체 응답을 받는 객체
public record KisPriceRes(

        // API 응답 코드 (정상: 0)
        @JsonProperty("rt_cd")
        String returnCode,

        // API 메시지 코드
        @JsonProperty("msg_cd")
        String messageCode,

        // API 응답 메시지
        @JsonProperty("msg1")
        String message,

        // 실제 시세 데이터
        @JsonProperty("output")
        Output output
) {
    // 우리가 실제로 사용하는 핵심 데이터
    public record Output(

            // 현재가
            @JsonProperty("stck_prpr")
            String currentPrice,

            // 전일 대비 금액
            @JsonProperty("prdy_vrss")
            String priceDifference,

            // 전일 대비 부호
            @JsonProperty("prdy_vrss_sign")
            String changeSign,

            // 전일 대비 등락률
            @JsonProperty("prdy_ctrt")
            String changeRate
    ) {
    }
}