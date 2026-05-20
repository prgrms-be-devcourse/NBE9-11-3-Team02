package com.back.together02be.ranking.service;

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.ranking.entity.Ranking;
import com.back.together02be.ranking.entity.RankingSeason;
import com.back.together02be.ranking.entity.RankingSnapshotType;
import com.back.together02be.ranking.repository.RankingRepository;
import com.back.together02be.users.entity.Users;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RankingSnapshotService {

    private final RankingRepository rankingRepository;
    private final com.back.together02be.asset.repository.UserAccountRepository userAccountRepository;
    private final RankingSeasonService rankingSeasonService;
    private final RankingAssetCalculator rankingAssetCalculator;

    // DAILY 랭킹을 생성하는 메서드.
    @Transactional
    public void createDailySnapshot(LocalDate snapshotDate) {
        rankingRepository.deleteRankings(RankingSnapshotType.DAILY, snapshotDate);

        List<RankingCandidate> candidates = getTop5Candidates();
        saveRankings(candidates, RankingSnapshotType.DAILY, snapshotDate);

        log.info("DAILY ranking created: {}", snapshotDate);
    }

    // MONTHLY 랭킹을 생성하는 메서드.
    @Transactional
    public void createMonthlySnapshot(LocalDate snapshotDate) {
        if (rankingRepository.existsRanking(RankingSnapshotType.MONTHLY, snapshotDate)) {
            throw new IllegalStateException("이미 해당 날짜의 MONTHLY 랭킹이 존재합니다.");
        }

        List<RankingCandidate> candidates = getTop5Candidates();
        saveRankings(candidates, RankingSnapshotType.MONTHLY, snapshotDate);

        log.info("MONTHLY ranking created: {}", snapshotDate);
    }

    // 전체 유저를 수익률 기준으로 정렬해 top5만 뽑는다.
    private List<RankingCandidate> getTop5Candidates() {
        // 1. 전체 유저 계좌 리스트를 한 번에 조회.
        List<UserAccount> userAccounts = userAccountRepository.findAll();

        // 2. 계산기에게 리스트를 통째로 넘겨 '유저ID별 총자산 Map'을 단 1번의 주식 쿼리로 얻어옴.
        Map<Long, Long> totalAssetMap = rankingAssetCalculator.calculateAllUsersTotalAsset(userAccounts);

        // 3. 루프 내부에서 더 이상 DB 조회를 하지 않고, 스트림 연산을 수행.
        return userAccounts.stream()
                .map(userAccount -> toCandidate(userAccount, totalAssetMap)) // 🔄 파라미터 추가 수정
                .sorted(
                        Comparator.comparing(RankingCandidate::profitRate, Comparator.reverseOrder())
                                .thenComparing(RankingCandidate::totalAsset, Comparator.reverseOrder())
                                .thenComparing(candidate -> candidate.user().getId())
                )
                .limit(5)
                .toList();
    }

    // 미리 계산해 둔 totalAssetMap을 전달받아 메모리에서 값을 Mapping.
    private RankingCandidate toCandidate(UserAccount userAccount, Map<Long, Long> totalAssetMap) {
        Users user = userAccount.getUsers();
        Long userId = user.getId();

        // 기존의 코드를 지우고, 미리 메모리에 매핑된 Map에서 자산 정보를 즉시 꺼냄
        long totalAsset = totalAssetMap.getOrDefault(userId, 0L);

        RankingSeason season = rankingSeasonService.getActiveSeason(userId);
        BigDecimal profitRate = calculateProfitRate(totalAsset, season.getBaseAsset());

        return new RankingCandidate(user, totalAsset, profitRate);
    }

    // 수익률은 시즌 기준 자산 대비로 계산한다.
    private BigDecimal calculateProfitRate(long totalAsset, long baseAsset) {
        if (baseAsset <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.valueOf(totalAsset - baseAsset)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(baseAsset), 2, RoundingMode.HALF_UP);
    }

    // top5 후보를 실제 Ranking 엔티티로 저장한다.
    private void saveRankings(
            List<RankingCandidate> candidates,
            RankingSnapshotType snapshotType,
            LocalDate snapshotDate
    ) {
        int rank = 1;

        for (RankingCandidate candidate : candidates) {
            Ranking ranking = new Ranking(
                    candidate.user(),
                    rank++,
                    candidate.profitRate(),
                    candidate.totalAsset(),
                    snapshotType,
                    snapshotDate
            );

            rankingRepository.save(ranking);
        }
    }

    // 계산 중에만 쓰는 내부 객체이다.
    private record RankingCandidate(
            Users user,
            Long totalAsset,
            BigDecimal profitRate
    ) {
    }
}