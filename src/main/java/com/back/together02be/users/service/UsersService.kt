package com.back.together02be.users.service

import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.global.extend.getOrThrow
import com.back.together02be.global.util.JwtUtil
import com.back.together02be.ranking.service.RankingSeasonService
import com.back.together02be.users.dto.request.LoginReq
import com.back.together02be.users.dto.request.SignupReq
import com.back.together02be.users.entity.Users
import com.back.together02be.users.repository.UsersRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class UsersService (
    private val usersRepository: UsersRepository,
    private val userAccountRepository: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val rankingSeasonService: RankingSeasonService,

    @param:Value("\${jwt.secret}") private val jwtSecret: String,
    @param:Value("\${jwt.access-expire-seconds}") private val accessExpireSeconds: Long,
    @param:Value("\${jwt.refresh-expire-seconds}") private val refreshExpireSeconds: Long
) {

    @Transactional
    fun signup(req: SignupReq) {
        require(usersRepository.findByUsername(req.username) == null) { "이미 사용중인 아이디입니다." }

        require(req.password == req.passwordConfirm) { "비밀번호가 일치하지 않습니다." }

        // 비밀번호는 null이 아니라고 알려주기 위해서 명확히 알려주는 방식 사용
        val encodedPassword = requireNotNull(passwordEncoder.encode(req.password)) {
            "비밀번호 암호화에 실패했습니다."
        }

        val user = Users(
            req.username,
//            passwordEncoder.encode(req.password)!!
            encodedPassword,
            req.nickname
        )

        usersRepository.save<Users>(user)
        userAccountRepository.save<UserAccount>(UserAccount(user, 0L, INITIAL_DEPOSIT))
        rankingSeasonService.createSeasonForUser(user, LocalDate.now())
    }

    fun count(): Long {
        return usersRepository.count()
    }

    @Transactional
    fun login(req: LoginReq): Array<String> {
        val user = usersRepository
            .findByUsername(req.username)
            .getOrThrow { IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.") }

        require(passwordEncoder.matches(req.password, user.password)) { "아이디 또는 비밀번호가 올바르지 않습니다." }

        // AccessToken 발급
        val accessToken = JwtUtil.generateAccessToken(
            jwtSecret,
            accessExpireSeconds,
            createAccessTokenBody(user)
        )

        // RefreshToken 발급
        val refreshToken: String = UUID.randomUUID().toString()
        user.updateRefreshToken(
            refreshToken,
            LocalDateTime.now().plusSeconds(refreshExpireSeconds)
        )

        return arrayOf(accessToken, refreshToken)
    }

    @Transactional
    fun logout(refreshToken: String) {
        val user = usersRepository
            .findByRefreshToken(refreshToken)
            .getOrThrow { IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.") }

        user.clearRefreshToken()
    }

    @Transactional
    fun reissueToken(refreshToken: String): Array<String> {
        val user = usersRepository
            .findByRefreshToken(refreshToken)
            .getOrThrow { IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.") }

        val refreshTokenExpiration = user.refreshTokenExpiration
            .getOrThrow { IllegalArgumentException("리프레시 토큰이 만료되었습니다.") }

        require(!refreshTokenExpiration.isBefore(LocalDateTime.now())) {
            "리프레시 토큰이 만료되었습니다."
        }

        // AccessToken 갱신
        val newAccessToken = JwtUtil.generateAccessToken(
            jwtSecret,
            accessExpireSeconds,
            createAccessTokenBody(user)
        )

        // RefreshToken 갱신
        val newRefreshToken: String = UUID.randomUUID().toString()
        user.updateRefreshToken(
            newRefreshToken,
            LocalDateTime.now().plusSeconds(refreshExpireSeconds)
        )

        return arrayOf(newAccessToken, newRefreshToken)
    }

    private fun createAccessTokenBody(user: Users): Map<String, Any> {
        return mapOf<String, Any>(
            "id" to user.id,
            "username" to user.username,
            "nickname" to user.nickname
        )
    }

    companion object {
        private const val INITIAL_DEPOSIT = 50000000L
    }
}
