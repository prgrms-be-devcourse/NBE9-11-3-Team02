package com.back.together02be.infra.kis.rest.repository

import com.back.together02be.infra.kis.rest.entity.KisAccessToken
import org.springframework.data.jpa.repository.JpaRepository

// Kotlin 전환 포인트:
// 1. JpaRepository의 제네릭 타입에서 불필요한 Nullable(?) 기호를 제거하여 명확한 타입(KisAccessToken, Long)을 선언한다.
interface KisAccessTokenRepository : JpaRepository<KisAccessToken, Long> {

    // 2. 자바의 무거운 Optional 래퍼 객체 대신, 코틀린 언어 차원에서 지원하는 Nullable 타입(?)을 반환 타입으로 사용한다.
    // Spring Data JPA는 코틀린 환경에서 반환 타입에 ?가 붙어있으면 데이터가 없을 때 자동으로 null을 반환해 준다.
    fun findTopByOrderByIdDesc(): KisAccessToken?
}