package com.back.together02be.global.springDoc

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(title = "모의투자 투게더 API", version = "beta", description = "2차 프로젝트 API"),
    // 코틀린에서는 어노테이션 배열 속성에 명시적으로 대괄호([])를 사용합니다.
    security = [SecurityRequirement(name = "bearerAuth")]
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
class SpringDoc {

    @Bean
    fun stocksApi() = GroupedOpenApi.builder()
        .group("종목 조회 API")
        .pathsToMatch("/api/stocks/**")
        .build()

    @Bean
    fun usersApi() = GroupedOpenApi.builder()
        .group("유저 API")
        .pathsToMatch("/api/users/**")
        .build()

    @Bean
    fun tradeApi() = GroupedOpenApi.builder()
        .group("거래 API")
        .pathsToMatch("/api/trades/**")
        .build()

    @Bean
    fun assetApi() = GroupedOpenApi.builder()
        .group("보유 자산 조회 API")
        .pathsToMatch("/api/asset/**")
        .build()

    @Bean
    fun achievementApi() = GroupedOpenApi.builder() // 네이밍 컨벤션에 맞춰 소문자로 시작
        .group("보유 업적 조회 API")
        .pathsToMatch("/api/achievements/**")
        .build()
}