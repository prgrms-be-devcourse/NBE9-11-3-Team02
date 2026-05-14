package com.back.together02be.ranking.entity;

import com.back.together02be.global.entity.BaseEntity;
import com.back.together02be.users.entity.Users;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
@Table(
        indexes = {
                @Index(name = "idx_ranking_season_user_active", columnList = "user_id, active")
        }
)
public class RankingSeason extends BaseEntity {

    // 한 유저는 월별 기준 자산 기록을 여러 개 가질 수 있다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    // 이번 월 수익률 계산의 기준 자산이다.
    @Column(nullable = false)
    private Long baseAsset;

    // 월 시작일이다.
    @Column(nullable = false)
    private LocalDate startDate;

    // 월 종료일이며 진행 중이면 null이다.
    @Column
    private LocalDate endDate;

    // 현재 활성 월 여부이다.
    @Column(nullable = false)
    private boolean active;

    public RankingSeason(Users user, Long baseAsset, LocalDate startDate) {
        this.user = user;
        this.baseAsset = baseAsset;
        this.startDate = startDate;
        this.active = true;
    }

    // 현재 월을 종료 상태로 변경한다.
    public void close(LocalDate endDate) {
        this.endDate = endDate;
        this.active = false;
    }
}