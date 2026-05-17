package com.back.together02be.asset.repository

import com.back.together02be.asset.entity.UserStock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserStockRepository : JpaRepository<UserStock, Long> {

    fun findByUsersIdAndStockId(usersId: Long, stockId: Long): UserStock?

    fun findAllByUsersId(usersId: Long): List<UserStock>

    // 더티 체킹을 사용하지 않으므로, 연산 후 영속성 컨텍스트를 비워줘야 최신 데이터가 반영됩니다.
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

    // delete도 동시성 제어를 위해 사용
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserStock u WHERE u.users.id = :userId AND u.stock.id = :stockId AND u.quantity = 0")
    fun deleteByUserAndStock(
        @Param("userId") userId: Long,
        @Param("stockId") stockId: Long
    )

    // fun delete(userStock: UserStock)
}