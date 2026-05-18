package com.back.together02be.users

import com.back.together02be.asset.entity.UserAccount
import com.back.together02be.asset.repository.UserAccountRepository
import com.back.together02be.global.util.JwtUtil
import com.back.together02be.ranking.service.RankingSeasonService
import com.back.together02be.users.dto.request.LoginReq
import com.back.together02be.users.dto.request.SignupReq
import com.back.together02be.users.entity.Users
import com.back.together02be.users.repository.UsersRepository
import com.back.together02be.users.service.UsersService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.stubbing.Answer
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.util.ReflectionTestUtils
import java.time.LocalDateTime
import java.util.*

@ExtendWith(MockitoExtension::class)
internal class UsersServiceTest {
    @Mock
    lateinit var usersRepository: UsersRepository

    @Mock
    lateinit var userAccountRepository: UserAccountRepository

    @Mock
    lateinit var passwordEncoder: PasswordEncoder

    @Mock
    lateinit var rankingSeasonService: RankingSeasonService

    lateinit var usersService: UsersService

    @BeforeEach
    fun setUp() {
        usersService = UsersService(
            usersRepository,
            userAccountRepository,
            passwordEncoder,
            rankingSeasonService,
            "test-secret-key-must-be-32-bytes!!",
            900L,
            604800L
        )
    }

    @Test
    @DisplayName("정상 회원가입 — Users 저장, UserAccount(5000만원) 생성")
    fun t1() {
        val req = SignupReq("testuser", "password1!", "password1!", "닉네임")

        Mockito.`when`(usersRepository.findByUsername("testuser"))
            .thenReturn(Optional.empty<Users>())
        Mockito.`when`(passwordEncoder.encode("password1!")).thenReturn("encodedPassword")
        Mockito.`when`(usersRepository.save(ArgumentMatchers.any(Users::class.java)))
            .thenAnswer(Answer { inv: InvocationOnMock -> inv.getArgument<Users>(0) })

        usersService.signup(req)

        val captor = ArgumentCaptor.forClass(UserAccount::class.java)
        Mockito.verify(userAccountRepository).save(captor.capture())
        assertThat(captor.value.deposit).isEqualTo(50000000L)
        assertThat(captor.value.totalPurchase).isEqualTo(0L)
    }

    @Test
    @DisplayName("중복 아이디 — IllegalArgumentException 발생")
    fun t2() {
        val req = SignupReq("testuser", "password1!", "password1!", "닉네임")

        Mockito.`when`(usersRepository.findByUsername("testuser"))
            .thenReturn(Optional.of<Users>(Users("testuser", "encoded", "닉네임")))

        assertThatThrownBy { usersService.signup(req) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("이미 사용중인 아이디입니다.")

        Mockito.verify(usersRepository, Mockito.never()).save(ArgumentMatchers.any(Users::class.java))
    }

    @Test
    @DisplayName("비밀번호 불일치 — IllegalArgumentException 발생")
    fun t3() {
        val req = SignupReq("testuser", "password1!", "different!", "닉네임")

        Mockito.`when`(usersRepository.findByUsername("testuser"))
            .thenReturn(Optional.empty<Users>())

        assertThatThrownBy { usersService.signup(req) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("비밀번호가 일치하지 않습니다.")

        Mockito.verify(usersRepository, Mockito.never()).save(ArgumentMatchers.any(Users::class.java))
    }

    @Test
    @DisplayName("정상 로그인 — AccessToken, RefreshToken 반환 및 DB 저장")
    fun t4() {
        val req = LoginReq("testuser", "password1!")
        val user = Users("testuser", "encodedPassword", "닉네임")
        ReflectionTestUtils.setField(user, "id", 1L)

        Mockito.`when`(usersRepository.findByUsername("testuser"))
            .thenReturn(Optional.of<Users>(user))
        Mockito.`when`(passwordEncoder.matches("password1!", "encodedPassword")).thenReturn(true)

        val tokens: Array<String> = usersService.login(req)
        val payload = JwtUtil.payloadOrNull(tokens[0], "test-secret-key-must-be-32-bytes!!")

        assertThat(tokens).hasSize(2)
        assertThat(tokens[0]).isNotBlank()
        assertThat(tokens[1]).isNotBlank()
        assertThat(payload).isNotNull()
        val tokenPayload = requireNotNull(payload)
        assertThat((tokenPayload["id"] as Number).toLong()).isEqualTo(1L)
        assertThat(tokenPayload["username"]).isEqualTo("testuser")
        assertThat(tokenPayload["nickname"]).isEqualTo("닉네임")
        assertThat(user.refreshToken).isEqualTo(tokens[1])
        assertThat(user.refreshTokenExpiration).isAfter(LocalDateTime.now())
    }

    @Test
    @DisplayName("존재하지 않는 아이디 — IllegalArgumentException 발생")
    fun t5() {
        val req = LoginReq("nobody", "password1!")

        Mockito.`when`(usersRepository.findByUsername("nobody")).thenReturn(Optional.empty<Users>())

        assertThatThrownBy { usersService.login(req) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("아이디 또는 비밀번호가 올바르지 않습니다.")
    }

    @Test
    @DisplayName("비밀번호 오류 — IllegalArgumentException 발생")
    fun t6() {
        val req = LoginReq("testuser", "wrongpass!")
        val user = Users("testuser", "encodedPassword", "닉네임")

        Mockito.`when`(usersRepository.findByUsername("testuser"))
            .thenReturn(Optional.of<Users>(user))
        Mockito.`when`(passwordEncoder.matches("wrongpass!", "encodedPassword")).thenReturn(false)

        assertThatThrownBy { usersService.login(req) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("아이디 또는 비밀번호가 올바르지 않습니다.")
    }

    @Test
    @DisplayName("정상 로그아웃 — RefreshToken 초기화")
    fun t7() {
        val user = Users("testuser", "encoded", "닉네임")
        user.updateRefreshToken("valid-token", LocalDateTime.now().plusDays(7))

        Mockito.`when`(usersRepository.findByRefreshToken("valid-token"))
            .thenReturn(Optional.of<Users>(user))

        usersService.logout("valid-token")

        assertThat(user.refreshToken).isNull()
        assertThat(user.refreshTokenExpiration).isNull()
    }

    @Test
    @DisplayName("존재하지 않는 RefreshToken으로 로그아웃 — IllegalArgumentException 발생")
    fun t8() {
        Mockito.`when`(usersRepository.findByRefreshToken("invalid-token"))
            .thenReturn(Optional.empty<Users>())

        assertThatThrownBy { usersService.logout("invalid-token") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("유효하지 않은 리프레시 토큰입니다.")
    }

    @Test
    @DisplayName("정상 토큰 재발급 — 새 AccessToken, RefreshToken 반환")
    fun t9() {
        val user = Users("testuser", "encoded", "닉네임")
        ReflectionTestUtils.setField(user, "id", 1L)
        user.updateRefreshToken("old-token", LocalDateTime.now().plusDays(7))

        Mockito.`when`(usersRepository.findByRefreshToken("old-token"))
            .thenReturn(Optional.of<Users>(user))

        val tokens: Array<String> = usersService.reissueToken("old-token")
        val payload = JwtUtil.payloadOrNull(tokens[0], "test-secret-key-must-be-32-bytes!!")

        assertThat(tokens).hasSize(2)
        assertThat(tokens[0]).isNotBlank()
        assertThat(tokens[1]).isNotBlank().isNotEqualTo("old-token")
        assertThat(payload).isNotNull()
        val tokenPayload = requireNotNull(payload)
        assertThat((tokenPayload["id"] as Number).toLong()).isEqualTo(1L)
        assertThat(tokenPayload["username"]).isEqualTo("testuser")
        assertThat(tokenPayload["nickname"]).isEqualTo("닉네임")
        assertThat(user.refreshToken).isEqualTo(tokens[1])
    }

    @Test
    @DisplayName("존재하지 않는 RefreshToken으로 재발급 — IllegalArgumentException 발생")
    fun t10() {
        Mockito.`when`(usersRepository.findByRefreshToken("ghost-token"))
            .thenReturn(Optional.empty<Users>())

        assertThatThrownBy { usersService.reissueToken("ghost-token") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("유효하지 않은 리프레시 토큰입니다.")
    }

    @Test
    @DisplayName("만료된 RefreshToken — IllegalArgumentException 발생")
    fun t11() {
        val user = Users("testuser", "encoded", "닉네임")
        user.updateRefreshToken("expired-token", LocalDateTime.now().minusSeconds(1))

        Mockito.`when`(usersRepository.findByRefreshToken("expired-token"))
            .thenReturn(Optional.of<Users>(user))

        assertThatThrownBy { usersService.reissueToken("expired-token") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("리프레시 토큰이 만료되었습니다.")
    }
}
