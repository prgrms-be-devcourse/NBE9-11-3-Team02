package com.back.together02be.users.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupReq(

    @field:NotBlank(message = "아이디를 입력해주세요.")
    @field:Size(min = 4, max = 30, message = "아이디는 4자 이상 30자 이하로 입력해주세요.")
    val username: String,

    @field:NotBlank(message = "비밀번호를 입력해주세요.")
    @field:Size(min = 8, message = "비밀번호는 8자 이상 입력해주세요.")
    val password: String,

    @field:NotBlank(message = "비밀번호 확인을 입력해주세요.")
    val passwordConfirm: String,

    @field:NotBlank(message = "닉네임을 입력해주세요.")
    @field:Size(min = 2, max = 30, message = "닉네임은 2자 이상 30자 이하로 입력해주세요.")
    val nickname: String
)
