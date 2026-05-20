package com.back.together02be.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig(
    private val corsProperties: CorsProperties
) {

    @Bean
    fun corsConfigurationSource(): UrlBasedCorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = corsProperties.allowedOrigins
        configuration.allowedMethods = corsProperties.allowedMethods
        configuration.allowedHeaders = corsProperties.allowedHeaders
        configuration.allowCredentials = corsProperties.allowCredentials
        configuration.maxAge = corsProperties.maxAge

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
