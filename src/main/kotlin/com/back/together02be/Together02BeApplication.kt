package com.back.together02be

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableCaching
@EnableAsync
@ConfigurationPropertiesScan
class Together02BeApplication

fun main(args: Array<String>) {
    runApplication<Together02BeApplication>(*args)
}
