package com.back.together02be.ranking.service;

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RankingAssetCalculator {

    private final UserStockRepository userStockRepository;
    private final RealTimeStockPriceStore realTimeStockPriceStore;

    // 유저의 현재 총자산을 계산한다.
    public long calculateTotalAsset(UserAccount userAccount) {
        List<UserStock> userStocks = userStockRepository.findAllByUsersId(userAccount.getUsers().getId());

        long stockEvaluationAmount = userStocks.stream()
                .mapToLong(this::calculateStockEvaluationAmount)
                .sum();

        return userAccount.getDeposit() + stockEvaluationAmount;
    }

    // 보유 종목 1건의 평가금액을 계산한다.
    public long calculateStockEvaluationAmount(UserStock userStock) {
        String stockCode = userStock.getStock().getStockCode();
        RealtimeStockPrice realtimeStockPrice = realTimeStockPriceStore.get(stockCode);

        long currentPrice = extractCurrentPrice(realtimeStockPrice, userStock.getAveragePrice());

        return currentPrice * userStock.getQuantity();
    }

    // 실시간 가격이 없으면 평균매입가를 대신 사용한다.
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