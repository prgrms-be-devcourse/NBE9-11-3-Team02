package com.back.together02be.infra.kis.rest.dto

import com.fasterxml.jackson.annotation.JsonProperty

// Kotlin 전환 포인트:
// 외부 API의 JSON 키(snake_case)와 코틀린의 프로퍼티명(camelCase)이 다르므로,
// @JsonProperty를 사용하여 명시적으로 매핑해 주어야 null 값이 들어오는 것을 방지할 수 있다.
data class KisTokenRes(
    @JsonProperty("access_token")
    val accessToken: String,

    @JsonProperty("token_type")
    val tokenType: String,

    @JsonProperty("expires_in")
    val expiresIn: Int
)