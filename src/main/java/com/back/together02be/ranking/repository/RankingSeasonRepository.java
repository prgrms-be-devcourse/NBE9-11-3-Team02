package com.back.together02be.ranking.repository;

import com.back.together02be.ranking.entity.RankingSeason;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RankingSeasonRepository extends JpaRepository<RankingSeason, Long> {

    // 현재 활성 시즌을 유저 기준으로 조회한다.
    Optional<RankingSeason> findByUserIdAndActiveTrue(Long userId);

    // 현재 활성 시즌 전체를 조회한다.
    List<RankingSeason> findByActiveTrue();
}