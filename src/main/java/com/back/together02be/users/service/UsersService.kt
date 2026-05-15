package com.back.together02be.users.service

import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.global.util.JwtUtil
import com.back.together02be.ranking.service.RankingSeasonService
import com.back.together02be.users.dto.request.LoginReq
import com.back.together02be.users.dto.request.SignupReq
import com.back.together02be.users.entity.Users
import com.back.together02be.users.repository.UsersRepository
import lombok.RequiredArgsConstructor
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.function.Supplier

@Service
@RequiredArgsConstructor
class UsersService (
    private val usersRepository: UsersRepository,
    private val userAccountRepository: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val rankingSeasonService: RankingSeasonService
) {

    @Value("\${jwt.secret}")
    private val jwtSecret: String? = null

    @Value("\${jwt.access-expire-seconds}")
    private val accessExpireSeconds: Long = 0

    @Value("\${jwt.refresh-expire-seconds}")
    private val refreshExpireSeconds: Long = 0

    @Transactional
    fun signup(req: SignupReq) {
        require(!usersRepository.findByUsername(req.username).isPresent()) { "이미 사용중인 아이디입니다." }

        require(req.password == req.passwordConfirm) { "비밀번호가 일치하지 않습니다." }

        // db에 [회원가입 한 user] 저장
        val user = Users(
            req.username,
            passwordEncoder.encode(req.password),
            req.nickname
        )

        usersRepository.save<Users?>(user)
        userAccountRepository.save<UserAccount?>(UserAccount(user, 0L, INITIAL_DEPOSIT))
        rankingSeasonService.createSeasonForUser(user, LocalDate.now())
    }

    fun count(): Long {
        return usersRepository.count()
    }

    @Transactional
    fun login(req: LoginReq): Array<String?> {
        val user = usersRepository
            .findByUsername(req.username)
            .orElseThrow<IllegalArgumentException?>(
                Supplier { IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.") }
            )

        require(passwordEncoder.matches(req.password, user.password)) { "아이디 또는 비밀번호가 올바르지 않습니다." }

        // AccessToken 발급
        val accessToken = JwtUtil.generateAccessToken(
            jwtSecret,
            accessExpireSeconds,
            createAccessTokenBody(user)
        )

        // RefreshToken 발급
        val refreshToken: String? = UUID.randomUUID().toString()
        user.updateRefreshToken(
            refreshToken,
            LocalDateTime.now().plusSeconds(refreshExpireSeconds)
        )

        return arrayOf<String?>(accessToken, refreshToken)
    }

    @Transactional
    fun logout(refreshToken: String) {
        val user = usersRepository
            .findByRefreshToken(refreshToken)
            .orElseThrow<IllegalArgumentException?>(
                Supplier { IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.") }
            )

        user.clearRefreshToken()
    }

    @Transactional
    fun reissueToken(refreshToken: String): Array<String?> {
        val user = usersRepository
            .findByRefreshToken(refreshToken)
            .orElseThrow<IllegalArgumentException?>(
                Supplier { IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.") }
            )

        // 만료시간(10시) < 현재시간(11시)
        require(!user.getRefreshTokenExpiration().isBefore(LocalDateTime.now())) { "리프레시 토큰이 만료되었습니다." }

        // AccessToken 갱신
        val newAccessToken = JwtUtil.generateAccessToken(
            jwtSecret,
            accessExpireSeconds,
            createAccessTokenBody(user)
        )

        // RefreshToken 갱신
        val newRefreshToken: String? = UUID.randomUUID().toString()
        user.updateRefreshToken(
            newRefreshToken,
            LocalDateTime.now().plusSeconds(refreshExpireSeconds)
        )

        return arrayOf<String?>(newAccessToken, newRefreshToken)
    }

    private fun createAccessTokenBody(user: Users): MutableMap<String, Any> {
        return Map.of<String, Any>(
            "id", user.id,
            "username", user.username,
            "nickname", user.nickname
        )
    }

    companion object {
        private const val INITIAL_DEPOSIT = 50000000L
    }
}
