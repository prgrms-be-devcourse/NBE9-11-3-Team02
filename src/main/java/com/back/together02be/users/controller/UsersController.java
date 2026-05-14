package com.back.together02be.users.controller;

import com.back.together02be.global.apiRes.ApiRes;
import com.back.together02be.users.dto.request.LoginReq;
import com.back.together02be.users.dto.request.SignupReq;
import com.back.together02be.users.dto.response.UsersRes;
import com.back.together02be.users.service.UsersService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
@Tag(name = "UsersController", description = "유저 API")
public class UsersController {

    private final UsersService usersService;

    @PostMapping("/signup")
    @Operation(summary = "회원 가입")
    public ResponseEntity<ApiRes<Void>> signup(@RequestBody @Valid SignupReq req) {

        usersService.signup(req);
        return ResponseEntity.ok(
                new ApiRes<>("회원가입 성공", null)
        );
    }

    // tokens[0] : Access, tokens[1] : Refresh

    @PostMapping("/login")
    @Operation(summary = "로그인")
    public ResponseEntity<ApiRes<UsersRes>> login(@RequestBody @Valid LoginReq req, HttpServletResponse response) {

        String[] tokens = usersService.login(req);
        addRefreshTokenCookie(response, tokens[1]);
        return ResponseEntity.ok(
                new ApiRes<>("로그인 성공", new UsersRes(tokens[0]))
        );
    }

    @PostMapping("/token")
    @Operation(summary = "토큰 재발급")
    public ResponseEntity<ApiRes<UsersRes>> reissueToken(
            @CookieValue(name = "refreshToken") String refreshToken,
            HttpServletResponse response
    ) {
        String[] tokens = usersService.reissueToken(refreshToken);
        addRefreshTokenCookie(response, tokens[1]);
        return ResponseEntity.ok(
                new ApiRes<>("토큰 재발급 성공", new UsersRes(tokens[0]))
        );
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃")
    public ResponseEntity<ApiRes<Void>> logout(
            @CookieValue(name = "refreshToken") String refreshToken,
            HttpServletResponse response
    ) {
        usersService.logout(refreshToken);
        deleteRefreshTokenCookie(response);
        return ResponseEntity.ok(
                new ApiRes<>("로그아웃 성공", null)
        );
    }

    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setDomain("localhost");
        cookie.setSecure(true);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private void deleteRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("refreshToken", "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setDomain("localhost");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
