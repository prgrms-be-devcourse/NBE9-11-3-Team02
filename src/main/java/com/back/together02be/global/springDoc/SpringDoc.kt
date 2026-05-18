package com.back.together02be.global.springDoc;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(title = "모의투자 투게더 API", version = "beta", description = "2차 프로젝트 API"),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class SpringDoc {

    @Bean
    public GroupedOpenApi stocksApi() { // 예시
        return GroupedOpenApi.builder()
                .group("종목 조회 API")
                .pathsToMatch("/api/stocks/**")
                .build();
    }

    @Bean
    public GroupedOpenApi usersApi() {
        return GroupedOpenApi.builder()
                .group("유저 API")
                .pathsToMatch("/api/users/**")
                .build();
    }

    @Bean
    public GroupedOpenApi tradeApi() {
        return GroupedOpenApi.builder()
                .group("거래 API")
                .pathsToMatch("/api/trades/**")
                .build();
    }

    @Bean
    public GroupedOpenApi assetApi() {
        return GroupedOpenApi.builder()
                .group("보유 자산 조회 API")
                .pathsToMatch("/api/asset/**")
                .build();
    }

    @Bean
    public GroupedOpenApi AchievementApi() {
        return GroupedOpenApi.builder()
                .group("보유 업적 조회 API")
                .pathsToMatch("/api/achievements/**")
                .build();
    }
}