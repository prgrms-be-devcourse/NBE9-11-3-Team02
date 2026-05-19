package com.back.together02be.users.repository

import com.back.together02be.users.entity.Users
import org.springframework.data.jpa.repository.JpaRepository

interface UsersRepository : JpaRepository<Users, Long> {
    fun findByUsername(username: String): Users?
    fun findByRefreshToken(refreshToken: String): Users?
    fun existsByUsername(username: String): Boolean
}
