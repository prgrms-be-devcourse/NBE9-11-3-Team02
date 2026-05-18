package com.back.together02be.infra.kis.rest.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class KisTokenRes(
    @JsonProperty("access_token")
    val accessToken: String?,

    @JsonProperty("token_type")
    val tokenType: String?,

    @JsonProperty("expires_in")
    val expiresIn: Int?
)