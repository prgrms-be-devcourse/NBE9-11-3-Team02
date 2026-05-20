package com.back.together02be.infra.kis.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "kis")
data class KisProperties(
    val appKey: String,
    val appSecret: String,
    val restBaseUrl: String,
    val wsUrl: String
)
