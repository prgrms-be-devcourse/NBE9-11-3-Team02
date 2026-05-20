package com.back.together02be.achievement.entity

import com.back.together02be.global.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity

@Entity
class Achievement(
    @Column(nullable = false, unique = true)
    val code: String,

    @Column(nullable = false)
    var name: String,

    var description: String = "기본 설명 입니다."
) : BaseEntity()