package com.back.together02be.users.dto.request

import jakarta.validation.constraints.NotBlank

@JvmRecord
data class LoginReq(
    val username: @NotBlank(message = "아이디를 입력해주세요.") String?,

    val password: @NotBlank(message = "비밀번호를 입력해주세요.") String?
)
