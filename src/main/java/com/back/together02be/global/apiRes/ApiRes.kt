package com.back.together02be.global.apiRes

// Kotlin 전환 포인트:
// 스프링 생태계(Jackson 등)와의 완벽한 호환성을 유지하기 위해 @JvmRecord를 제거하고
// 순수 Kotlin의 data class 본연의 모습으로 사용한다.
data class ApiRes<T>(
    val message: String,
    val data: T?
)