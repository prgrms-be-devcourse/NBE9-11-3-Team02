package com.back.together02be.global.apiRes

data class ApiRes<T>(
    val message: String,
    val data: T?
)