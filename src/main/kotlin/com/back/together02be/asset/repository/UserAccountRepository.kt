package com.back.together02be.asset.repository

import com.back.together02be.asset.entity.UserAccount
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.query.Param
import java.util.Optional

interface UserAccountRepository : JpaRepository<UserAccount, Long> {

    fun findByUsersId(usersId: Long): Optional<UserAccount>

    // 비관적 락 설정
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(
        QueryHint(
            name = "jakarta.persistence.lock.timeout",
            value = "500" // 타임아웃 0.5초
        )
    )
    @Query("SELECT ua FROM UserAccount ua WHERE ua.users.id = :usersId")
    fun findByUsersIdWithLock(@Param("usersId") usersId: Long): Optional<UserAccount>

    @Modifying(clearAutomatically = true)
    @Query(
        "UPDATE UserAccount u SET u.deposit = u.deposit - :amount, u.totalPurchase = u.totalPurchase + :amount " +
            "WHERE u.users.id = :userId AND u.deposit >= :amount"
    )
    fun decreaseDepositIfSufficient(@Param("userId") userId: Long, @Param("amount") amount: Long): Int

    @Modifying
    @Query(
        "UPDATE UserAccount u SET u.deposit = u.deposit + :amount, u.totalPurchase = u.totalPurchase - :purchaseAmount " +
            "WHERE u.users.id = :userId"
    )
    fun updateDepositAndPurchase(
        @Param("userId") userId: Long,
        @Param("amount") amount: Long,
        @Param("purchaseAmount") purchaseAmount: Long
    ): Int
}