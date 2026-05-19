package com.back.together02be.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class AppConfig {

    @Bean
    fun clock(): Clock {
        // 서울 시간대를 기준으로 작동하는 기본 시스템 시계를 생성하여 빈으로 등록합니다.
        return Clock.system(ZoneId.of("Asia/Seoul"))
    }
}