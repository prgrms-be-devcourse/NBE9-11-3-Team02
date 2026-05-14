package com.back.together02be.users.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginReq(
        @NotBlank(message = "아이디를 입력해주세요.")
        String username,

        @NotBlank(message = "비밀번호를 입력해주세요.")
        String password
) {}
