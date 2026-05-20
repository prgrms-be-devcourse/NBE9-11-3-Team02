package com.back.together02be.ranking.service;

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserAccountRepository;
import com.back.together02be.asset.repository.UserStockRepository; // ➕ 직접 임포트
import com.back.together02be.ranking.entity.RankingSeason;
import com.back.together02be.ranking.repository.RankingSeasonRepository;
import com.back.together02be.users.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RankingSeasonService {

    private final RankingSeasonRepository rankingSeasonRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserStockRepository userStockRepository; // 🎯 여기에 직접 주입받아 사용합니다!
    private final RankingAssetCalculator rankingAssetCalculator;

    // 시즌 시작 시 전체 유저의 기준 자산을 새로 저장한다.
    @Transactional
    public void startSeason(LocalDate startDate) {
        List<UserAccount> accounts = userAccountRepository.findAll();

        // 계산기를 호출하여 모든 유저의 총자산을 단 1번의 주식 쿼리로 가져옴.
        Map<Long, Long> totalAssetMap = rankingAssetCalculator.calculateAllUsersTotalAsset(accounts);

        for (UserAccount account : accounts) {
            Long userId = account.getUsers().getId();

            if (rankingSeasonRepository.findByUserIdAndActiveTrue(userId).isPresent()) {
                continue;
            }

            // [N+1 해결] 메모리 Map에서 자산 값을 즉시 꺼냄.
            long totalAsset = totalAssetMap.getOrDefault(userId, 0L);

            RankingSeason season = new RankingSeason(account.getUsers(), totalAsset, startDate);
            rankingSeasonRepository.save(season);
        }
    }

    // 기존 활성 시즌을 모두 종료.
    @Transactional
    public void closeSeason(LocalDate endDate) {
        List<RankingSeason> activeSeasons = rankingSeasonRepository.findByActiveTrue();

        for (RankingSeason season : activeSeasons) {
            season.close(endDate);
        }
    }

    // 매월 1일 00:00:00 또는 00:01:00에 시즌을 리셋한다.
    @Transactional
    public void resetSeason(LocalDate endDate, LocalDate nextStartDate) {
        closeSeason(endDate);
        startSeason(nextStartDate);
    }

    // 현재 유저의 활성 시즌 정보를 가져온다.
    @Transactional(readOnly = true)
    public RankingSeason getActiveSeason(Long userId) {
        return rankingSeasonRepository.findByUserIdAndActiveTrue(userId)
                .orElseThrow(() -> new IllegalStateException("활성 시즌 정보가 없습니다. userId=" + userId));
    }

    // 단일 유저 시즌 생성 로직
    @Transactional
    public void createSeasonForUser(Users user, LocalDate startDate) {
        boolean exists = rankingSeasonRepository.findByUserIdAndActiveTrue(user.getId()).isPresent();

        if (exists) {
            return;
        }

        UserAccount account = userAccountRepository.findByUsersId(user.getId())
                .orElseThrow(() -> new IllegalStateException("계좌 정보가 없습니다. userId=" + user.getId()));

        // 해당 유저 1명의 주식 데이터를 깔끔하게 조회.
        List<UserStock> userStocks = userStockRepository.findAllByUsersId(user.getId());

        // 해당 유저의 주식 평가금액 계산
        long stockEvaluationAmount = userStocks.stream()
                .mapToLong(rankingAssetCalculator::calculateStockEvaluationAmount)
                .sum();

        // 최종 총자산 계산
        long totalAsset = account.getDeposit() + stockEvaluationAmount;

        RankingSeason season = new RankingSeason(user, totalAsset, startDate);
        rankingSeasonRepository.save(season);
    }
}