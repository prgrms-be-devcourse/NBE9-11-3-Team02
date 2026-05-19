package com.back.together02be.global.security

import com.back.together02be.users.entity.Users
import com.back.together02be.users.repository.UsersRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.test.util.ReflectionTestUtils

@ExtendWith(MockitoExtension::class)
internal class CustomUserDetailsServiceTest {
    @Mock
    lateinit var usersRepository: UsersRepository

    @Test
    @DisplayName("username으로 사용자 찾기 - SecurityUser 반환")
    fun loadUserByUsername_success() {
        // given
        val user = Users("testuser", "encodedPassword", "테스터")
        ReflectionTestUtils.setField(user, "id", 1L)

        Mockito.`when`(usersRepository.findByUsername("testuser"))
            .thenReturn(user)

        val service = CustomUserDetailsService(usersRepository)

        // when
        val result = service.loadUserByUsername("testuser")

        // then
        assertThat(result).isInstanceOf(SecurityUser::class.java)
        assertThat(result.username).isEqualTo("testuser")
        assertThat(result.password).isEqualTo("encodedPassword")

        val securityUser = result as SecurityUser

        assertThat(securityUser.id).isEqualTo(1L)
        assertThat(securityUser.nickname).isEqualTo("테스터")
    }

    @Test
    @DisplayName("존재하지 않는 username - UsernameNotFoundException")
    fun loadUserByUsername_notFound() {
        // given
        Mockito.`when`(usersRepository.findByUsername("missing"))
            .thenReturn(null)

        val service = CustomUserDetailsService(usersRepository)

        // when & then
        assertThatThrownBy { service.loadUserByUsername("missing") }
            .isInstanceOf(UsernameNotFoundException::class.java)
            .hasMessage("존재하지 않는 아이디입니다: missing")
    }
}
