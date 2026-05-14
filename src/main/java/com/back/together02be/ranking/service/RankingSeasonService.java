package com.back.together02be.ranking.service;

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.repository.UserAccountRepository;
import com.back.together02be.ranking.entity.RankingSeason;
import com.back.together02be.ranking.repository.RankingSeasonRepository;
import com.back.together02be.users.entity.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RankingSeasonService {

    private final RankingSeasonRepository rankingSeasonRepository;
    private final UserAccountRepository userAccountRepository;
    private final RankingAssetCalculator rankingAssetCalculator;

    // 시즌 시작 시 전체 유저의 기준 자산을 새로 저장한다.
    @Transactional
    public void startSeason(LocalDate startDate) {
        List<UserAccount> accounts = userAccountRepository.findAll();

        for (UserAccount account : accounts) {
            Long userId = account.getUsers().getId();

            if (rankingSeasonRepository.findByUserIdAndActiveTrue(userId).isPresent()) {
                continue;
            }

            long totalAsset = rankingAssetCalculator.calculateTotalAsset(account);
            RankingSeason season = new RankingSeason(account.getUsers(), totalAsset, startDate);
            rankingSeasonRepository.save(season);
        }
    }

    // 기존 활성 시즌을 모두 종료한다.
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

    @Transactional
    public void createSeasonForUser(Users user, LocalDate startDate) {
        boolean exists = rankingSeasonRepository.findByUserIdAndActiveTrue(user.getId()).isPresent();

        if (exists) {
            return;
        }

        UserAccount account = userAccountRepository.findByUsersId(user.getId())
                .orElseThrow(() -> new IllegalStateException("계좌 정보가 없습니다. userId=" + user.getId()));

        long totalAsset = rankingAssetCalculator.calculateTotalAsset(account);
        RankingSeason season = new RankingSeason(user, totalAsset, startDate);
        rankingSeasonRepository.save(season);
    }
}