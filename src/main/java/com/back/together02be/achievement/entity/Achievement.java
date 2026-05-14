package com.back.together02be.achievement.entity;

import com.back.together02be.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Achievement extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code; // 매핑 기준이 되는 고유 코드 (예: "FIRST_TRADE")

    @Column(nullable = false)
    private String name; // 예: "첫 주주 등극"

    private String description; // 예: "생애 첫 주식 매수 성공"
    private String reward;      // 예: "1,000 포인트"

    public Achievement(String code, String name, String description, String reward) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.reward = reward;
    }
}