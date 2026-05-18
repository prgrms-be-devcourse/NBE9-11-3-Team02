package com.back.together02be.ranking.repository;

import com.back.together02be.asset.entity.UserAccount;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RankingQueryRepository {

    private final EntityManager em;

    // 🎯 랭킹 산정 시 N+1 문제를 방지하기 위한 전용 Fetch Join 쿼리
    public List<UserAccount> findAllWithStocks() {
        return em.createQuery(
                "SELECT DISTINCT ua FROM UserAccount ua LEFT JOIN FETCH ua.userStocks",
                UserAccount.class
        ).getResultList();
    }
}