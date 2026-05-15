package com.back.together02be.users.repository

import com.back.together02be.users.entity.Users
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface UsersRepository : JpaRepository<Users, Long> {
    fun findByUsername(username: String): Optional<Users>
    fun findByRefreshToken(refreshToken: String): Optional<Users>
    fun existsByUsername(username: String?): Boolean
}