package com.back.together02be.asset.repository

import com.back.together02be.asset.entity.UserStock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface UserStockRepository : JpaRepository<UserStock, Long> {

    fun findByUsersIdAndStockId(usersId: Long, stockId: Long): Optional<UserStock>

    fun findAllByUsersId(users_id: Long): List<UserStock>

    @Modifying(clearAutomatically = true)
    @Query(
        "UPDATE UserStock u SET u.quantity = u.quantity - :sellQuantity " +
                "WHERE u.users.id = :userId AND u.stock.id = :stockId AND u.quantity >= :sellQuantity"
    )
    fun updateQuantity(
        @Param("userId") userId: Long,
        @Param("stockId") stockId: Long,
        @Param("sellQuantity") sellQuantity: Long
    ): Int

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserStock u WHERE u.users.id = :userId AND u.stock.id = :stockId AND u.quantity = 0")
    fun deleteByUserAndStock(@Param("userId") userId: Long, @Param("stockId") stockId: Long)
}