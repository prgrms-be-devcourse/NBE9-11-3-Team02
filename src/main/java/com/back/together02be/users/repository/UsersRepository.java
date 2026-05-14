package com.back.together02be.users.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.back.together02be.users.entity.Users;

import java.util.Optional;

public interface UsersRepository extends JpaRepository<Users, Long> {
    Optional<Users> findByUsername(String username);
    Optional<Users> findByRefreshToken(String refreshToken);
    boolean existsByUsername(String username);
}