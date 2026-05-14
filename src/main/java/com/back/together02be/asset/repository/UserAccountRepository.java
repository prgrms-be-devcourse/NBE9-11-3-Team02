package com.back.together02be.asset.repository;

import com.back.together02be.asset.entity.UserAccount;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
	Optional<UserAccount> findByUsersId(Long usersId);

    //비관적 락
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(
            name = "jakarta.persistence.lock.timeout",
            value = "500" //타임아웃 0.5초
    ))
    @Query("SELECT ua FROM UserAccount ua WHERE ua.users.id = :usersId")
    Optional<UserAccount> findByUsersIdWithLock(@Param("usersId") Long usersId);

	@Modifying(clearAutomatically = true)
	@Query("UPDATE UserAccount u SET u.deposit = u.deposit - :amount, u.totalPurchase = u.totalPurchase + :amount " +
		"WHERE u.users.id = :userId AND u.deposit >= :amount")
	int decreaseDepositIfSufficient(@Param("userId") Long userId, @Param("amount") Long amount);

    @Modifying
    @Query("UPDATE UserAccount u SET u.deposit = u.deposit + :amount, u.totalPurchase = u.totalPurchase - :purchaseAmount " +
            "WHERE u.users.id = :userId")
    int updateDepositAndPurchase(@Param("userId") Long userId,
                                 @Param("amount") Long amount,
                                 @Param("purchaseAmount") Long purchaseAmount);
}
