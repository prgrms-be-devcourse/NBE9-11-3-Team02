package com.back.together02be.asset.entity;

import com.back.together02be.global.entity.BaseEntity;
import com.back.together02be.users.entity.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class UserAccount extends BaseEntity {

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "users_id", nullable = false, unique = true)
	private Users users;

	@Column(name = "total_purchase", nullable = false)
	private Long totalPurchase;

	@Column(name = "deposit", nullable = false)
	private Long deposit;

	public UserAccount(Users user, Long totalPurchase, Long deposit){
		this.users = user;
		this.totalPurchase = totalPurchase;
		this.deposit = deposit;
	}

	public void decreaseDeposit(Long amount) {
		this.deposit -= amount;
	}

	public void increaseTotalPurchase(Long amount) {
		this.totalPurchase += amount;
	}

	public void addDeposit(Long amount) {
		this.deposit += amount;  // 누적 합산
	}

	public void subtractTotalPurchase(Long amount) {
		this.totalPurchase = Math.max(0L, this.totalPurchase - amount);
	}
}
