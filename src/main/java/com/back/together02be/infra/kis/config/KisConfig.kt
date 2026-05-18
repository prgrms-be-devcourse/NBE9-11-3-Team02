package com.back.together02be.infra.kis.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class KisConfig {
    @Bean
    fun restClient(): RestClient = RestClient.create()
}
