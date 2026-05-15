package com.back.together02be.users.controller

import com.back.together02be.global.apiRes.ApiRes
import com.back.together02be.users.dto.request.LoginReq
import com.back.together02be.users.dto.request.SignupReq
import com.back.together02be.users.dto.response.UsersRes
import com.back.together02be.users.service.UsersService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
@Tag(name = "UsersController", description = "유저 API")
class UsersController(
    private val usersService: UsersService
) {

    @PostMapping("/signup")
    @Operation(summary = "회원 가입")
    fun signup(@RequestBody @Valid req: SignupReq): ResponseEntity<ApiRes<Void>> {
        usersService.signup(req)
        return ResponseEntity.ok(
            ApiRes<Void>("회원가입 성공", null)
        )
    }

    // tokens[0] : Access, tokens[1] : Refresh
    @PostMapping("/login")
    @Operation(summary = "로그인")
    fun login(
        @RequestBody @Valid req: LoginReq,
        response: HttpServletResponse
    ): ResponseEntity<ApiRes<UsersRes>> {
        val tokens = usersService.login(req)
        addRefreshTokenCookie(response, tokens[1])
        return ResponseEntity.ok(
            ApiRes<UsersRes>("로그인 성공", UsersRes(tokens[0]))
        )
    }

    @PostMapping("/token")
    @Operation(summary = "토큰 재발급")
    fun reissueToken(
        @CookieValue(name = "refreshToken") refreshToken: String,
        response: HttpServletResponse
    ): ResponseEntity<ApiRes<UsersRes>> {
        val tokens = usersService.reissueToken(refreshToken)
        addRefreshTokenCookie(response, tokens[1])
        return ResponseEntity.ok(
            ApiRes<UsersRes>("토큰 재발급 성공", UsersRes(tokens[0]))
        )
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃")
    fun logout(
        @CookieValue(name = "refreshToken") refreshToken: String,
        response: HttpServletResponse
    ): ResponseEntity<ApiRes<Void>> {
        usersService.logout(refreshToken)
        deleteRefreshTokenCookie(response)
        return ResponseEntity.ok(
            ApiRes<Void>("로그아웃 성공", null)
        )
    }

    private fun addRefreshTokenCookie(response: HttpServletResponse, refreshToken: String?) {
        val cookie = Cookie("refreshToken", refreshToken)
        cookie.path = "/"
        cookie.isHttpOnly = true
        cookie.domain = "localhost"
        cookie.secure = true
        cookie.setAttribute("SameSite", "Strict")
        response.addCookie(cookie)
    }

    private fun deleteRefreshTokenCookie(response: HttpServletResponse) {
        val cookie = Cookie("refreshToken", "")
        cookie.path = "/"
        cookie.isHttpOnly = true
        cookie.domain = "localhost"
        cookie.maxAge = 0
        response.addCookie(cookie)
    }
}
