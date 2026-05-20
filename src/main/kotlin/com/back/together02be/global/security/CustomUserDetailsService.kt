package com.back.together02be.global.security

import com.back.together02be.global.extend.getOrThrow
import com.back.together02be.users.repository.UsersRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class CustomUserDetailsService(
    private val usersRepository: UsersRepository
) : UserDetailsService {

    @Throws(UsernameNotFoundException::class)
    override fun loadUserByUsername(username: String): UserDetails {
        val user = usersRepository.findByUsername(username)
            .getOrThrow { UsernameNotFoundException("존재하지 않는 아이디입니다: $username") }

        return SecurityUser(
            user.id,
            user.username,
            user.password,
            user.nickname,
            emptyList()
        )
    }
}
