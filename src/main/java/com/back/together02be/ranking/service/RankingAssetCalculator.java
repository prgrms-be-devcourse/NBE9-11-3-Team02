package com.back.together02be.ranking.service;

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RankingAssetCalculator {

    private final UserStockRepository userStockRepository;
    private final RealTimeStockPriceStore realTimeStockPriceStore;

    /**
     *  [N+1 해결 메서드]
     * 모든 유저의 자산을 '단 1번의 주식 조회 쿼리'만 사용하여 메모리 상에서 일괄 계산.
     * * @param userAccounts 상위 서비스(RankingSnapshotService 등)에서 넘겨준 전체 유저 계좌 리스트
     * @return 유저 ID별 총자산 Map (Key: Users의 ID, Value: 총자산)
     */
    public Map<Long, Long> calculateAllUsersTotalAsset(List<UserAccount> userAccounts) {
        if (userAccounts == null || userAccounts.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. 전 종목 주식 데이터를 딱 1번의 쿼리로 모조리 가져옴.
        List<UserStock> allUserStocks = userStockRepository.findAll();

        // 2. 가져온 주식들을 유저 ID(users_id) 기준으로 메모리 상에서 Map
        Map<Long, List<UserStock>> stocksPerUser = allUserStocks.stream()
                .collect(Collectors.groupingBy(userStock -> userStock.getUsers().getId()));

        // 3. 루프를 돌며 각 유저의 총자산을 계산하여 결과 Map에 담음.
        // 이 Loop 내부에서는 추가적인 DB 조회가 아닌 순수 자바 연산만 수행
        return userAccounts.stream().collect(Collectors.toMap(
                userAccount -> userAccount.getUsers().getId(),
                userAccount -> {
                    Long userId = userAccount.getUsers().getId();

                    // 메모리 Map에서 해당 유저의 주식 리스트를 획득 (주식이 없다면 빈 리스트)
                    List<UserStock> userStocks = stocksPerUser.getOrDefault(userId, List.of());

                    // 유저가 보유한 주식들의 평가 금액 총합 계산
                    long stockEvaluationAmount = userStocks.stream()
                            .mapToLong(this::calculateStockEvaluationAmount)
                            .sum();

                    // 최종 총자산 = 예수금(Deposit) + 주식 평가금액 총합
                    return userAccount.getDeposit() + stockEvaluationAmount;
                }
        ));
    }

    // 보유 종목 1건의 평가금액을 계산한다. (기존 로직 유지)
    public long calculateStockEvaluationAmount(UserStock userStock) {
        String stockCode = userStock.getStock().getStockCode();
        RealtimeStockPrice realtimeStockPrice = realTimeStockPriceStore.get(stockCode);

        long currentPrice = extractCurrentPrice(realtimeStockPrice, userStock.getAveragePrice());

        return currentPrice * userStock.getQuantity();
    }

    // 실시간 가격이 없으면 평균매입가를 대신 사용한다. (기존 로직 유지)
    public long extractCurrentPrice(RealtimeStockPrice realtimeStockPrice, Long fallbackPrice) {
        if (realtimeStockPrice == null
                || realtimeStockPrice.getPrice() == null
                || realtimeStockPrice.getPrice().isBlank()) {
            return fallbackPrice;
        }

        try {
            return Long.parseLong(realtimeStockPrice.getPrice());
        } catch (NumberFormatException e) {
            return fallbackPrice;
        }
    }
}