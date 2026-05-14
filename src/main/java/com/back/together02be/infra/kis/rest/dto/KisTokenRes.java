package com.back.together02be.infra.kis.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisTokenRes(

        @JsonProperty("access_token")
        String accessToken,

        @JsonProperty("token_type")
        String tokenType,

        @JsonProperty("expires_in")
        Integer expiresIn
) {
}