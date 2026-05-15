package com.back.together02be.asset.service;

import com.back.together02be.asset.dto.response.UserStockRes;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import com.back.together02be.users.entity.Users;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock UserStockRepository userStockRepository;
    @Mock RealTimeStockPriceStore realTimeStockPriceStore;

    @InjectMocks AssetService assetService;

    @Test
    @DisplayName("보유 종목 목록 및 실시간 현재가 정상 매핑 테스트")
    void getUserStocks_Success() {
        Long userId = 1L;
        Users user = new Users("testuser", "pw", "테스터");
        Stock stock1 = new Stock("005930", "삼성전자", null); // 실제 프로젝트의 Stock 생성자 스펙에 맞춰 수정 필요
        UserStock userStock1 = new UserStock(user, stock1, 10L, 50000L);

        RealtimeStockPrice mockPrice = RealtimeStockPrice.builder()
                .stockCode("005930")
                .price("75000")
                .build();

        when(userStockRepository.findAllByUsersId(userId)).thenReturn(List.of(userStock1));
        when(realTimeStockPriceStore.get("005930")).thenReturn(mockPrice);

        List<UserStockRes> result = assetService.getUserStocks(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).stockCode()).isEqualTo("005930");
        assertThat(result.get(0).quantity()).isEqualTo(10L);
        assertThat(result.get(0).currentPrice()).isEqualTo(75000L);
    }

    @Test
    @DisplayName("실시간 현재가 캐시 누락 시 0원으로 반환 방어 로직 테스트")
    void getUserStocks_WhenCacheMiss_ReturnsZero() {
        Long userId = 1L;
        Users user = new Users("testuser", "pw", "테스터");
        Stock stock1 = new Stock("005930", "삼성전자", null);
        UserStock userStock1 = new UserStock(user, stock1, 10L, 50000L);

        when(userStockRepository.findAllByUsersId(userId)).thenReturn(List.of(userStock1));
        when(realTimeStockPriceStore.get(anyString())).thenReturn(null); // 캐시 미스 상황 가정

        List<UserStockRes> result = assetService.getUserStocks(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).currentPrice()).isEqualTo(0L); // 0원으로 안전하게 처리되는지 확인
    }
}