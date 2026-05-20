package com.back.together02be.users.dto.request

import jakarta.validation.constraints.NotBlank

data class LoginReq(
    @field:NotBlank(message = "아이디를 입력해주세요.")
    val username: String,

    @field: NotBlank(message = "비밀번호를 입력해주세요.")
    val password: String
)
