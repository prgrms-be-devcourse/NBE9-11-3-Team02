package com.back.together02be.asset.entity;

import com.back.together02be.global.entity.BaseEntity;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.users.entity.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"users_id", "stock_id"}))
public class UserStock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "users_id", nullable = false)
    private Users users;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_id")
    private Stock stock;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false)
    private Long averagePrice;

    protected UserStock() {}

    public UserStock(Users users, Stock stock, Long quantity, Long averagePrice) {
        this.users = users;
        this.stock = stock;
        this.quantity = quantity;
        this.averagePrice = averagePrice;
    }

    public Users getUsers() { return users; }
    public Stock getStock() { return stock; }
    public Long getQuantity() { return quantity; }
    public Long getAveragePrice() { return averagePrice; }

    public void updateOnBuy(Long buyQuantity, Long buyPrice) {
        long newTotalCost = this.quantity * this.averagePrice + buyQuantity * buyPrice;
        this.quantity += buyQuantity;
        this.averagePrice = newTotalCost / this.quantity;
    }

    public void updateQuantity(Long newQuantity) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("보유 수량은 0보다 작을 수 없습니다.");
        }
        this.quantity = newQuantity;
    }
}
