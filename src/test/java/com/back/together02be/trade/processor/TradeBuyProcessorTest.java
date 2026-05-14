package com.back.together02be.trade.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserAccountRepository;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.global.idempotency.IdempotencyKey;
import com.back.together02be.global.idempotency.IdempotencyKeyRepository;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.entity.StockMarket;
import com.back.together02be.stock.repository.StockRepository;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import com.back.together02be.trade.dto.BuyReq;
import com.back.together02be.trade.dto.BuyRes;
import com.back.together02be.trade.entity.Trade;
import com.back.together02be.trade.repository.TradeRepository;
import com.back.together02be.users.entity.Users;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TradeBuyProcessorTest {

    @Mock RealTimeStockPriceStore stockPriceStore;
    @Mock UserAccountRepository userAccountRepository;
    @Mock UserStockRepository userStockRepository;
    @Mock StockRepository stockRepository;
    @Mock TradeRepository tradeRepository;
    @Mock IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock ObjectMapper objectMapper;
    @Mock ApplicationEventPublisher eventPublisher;

    @InjectMocks
    TradeBuyProcessor tradeBuyProcessor;

    private Users user;
    private Stock stock;
    private UserAccount account;
    private IdempotencyKey freshKey;

    @BeforeEach
    void setUp() {
        user = new Users("testuser", "password", "테스트유저");
        account = new UserAccount(user, 0L, 50_000_000L);
        stock = new Stock("005930", "삼성전자", StockMarket.KOSPI);
        ReflectionTestUtils.setField(stock, "id", 1L);

        freshKey = new IdempotencyKey("test-key", 1L);
        ReflectionTestUtils.setField(freshKey, "createdAt", LocalDateTime.now());

        lenient().when(idempotencyKeyRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.of(freshKey));
        lenient().when(stockRepository.findById(1L)).thenReturn(Optional.of(stock));
        lenient().when(userAccountRepository.decreaseDepositIfSufficient(anyLong(), anyLong())).thenReturn(1);
        lenient().when(userAccountRepository.findByUsersIdWithLock(1L)).thenReturn(Optional.of(account));
        lenient().when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            ReflectionTestUtils.setField(trade, "id", 1L);
            return trade;
        });
    }

    private RealtimeStockPrice mockPrice(String stockCode, long price) {
        return RealtimeStockPrice.builder()
                .stockCode(stockCode)
                .price(String.valueOf(price))
                .build();
    }

    @Test
    @DisplayName("정상 매수 (신규 보유종목) — 잔고 차감 쿼리 호출, 거래 내역·UserStock 저장")
    void 정상_매수_신규_보유종목() {
        long price = 70_000L;
        long quantity = 10L;
        long amount = price * quantity;

        when(stockPriceStore.get("005930")).thenReturn(mockPrice("005930", price));
        when(userStockRepository.findByUsersIdAndStockId(1L, 1L)).thenReturn(Optional.empty());

        BuyRes response = tradeBuyProcessor.processBuy(1L, "test-key", new BuyReq(1L, quantity, 70_000L));

        assertThat(response.price()).isEqualTo(price);
        assertThat(response.quantity()).isEqualTo(quantity);
        assertThat(response.amount()).isEqualTo(amount);

        verify(userAccountRepository).decreaseDepositIfSufficient(1L, amount);
        verify(userStockRepository).save(any(UserStock.class));
        verify(tradeRepository).save(any(Trade.class));
    }

    @Test
    @DisplayName("추가 매수 — 평균매입가 재계산 검증")
    void 추가_매수_평균매입가_재계산() {
        long existingQty = 10L;
        long existingAvgPrice = 60_000L;
        long newPrice = 70_000L;
        long newQty = 10L;
        long expectedAvgPrice = (existingQty * existingAvgPrice + newQty * newPrice) / (existingQty + newQty);

        UserStock existing = new UserStock(user, stock, existingQty, existingAvgPrice);

        when(stockPriceStore.get("005930")).thenReturn(mockPrice("005930", newPrice));
        when(userStockRepository.findByUsersIdAndStockId(1L, 1L)).thenReturn(Optional.of(existing));

        tradeBuyProcessor.processBuy(1L, "test-key", new BuyReq(1L, newQty, 70_000L));

        assertThat(existing.getQuantity()).isEqualTo(existingQty + newQty);
        assertThat(existing.getAveragePrice()).isEqualTo(expectedAvgPrice);
        verify(userStockRepository, never()).save(any());
    }

    @Test
    @DisplayName("잔고 부족 — @Modifying이 0 반환 시 예외 발생")
    void 잔고_부족_예외() {
        long price = 70_000L;
        long quantity = 1_000L;
        long amount = price * quantity;

        when(stockPriceStore.get("005930")).thenReturn(mockPrice("005930", price));
        when(userAccountRepository.decreaseDepositIfSufficient(1L, amount)).thenReturn(0);
        when(userAccountRepository.findByUsersId(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> tradeBuyProcessor.processBuy(1L, "test-key", new BuyReq(1L, quantity, 70_000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("잔고가 부족합니다");
    }

    @Test
    @DisplayName("슬리피지 초과 — 현재가가 예상가 대비 2% 초과 시 예외")
    void 슬리피지_초과_예외() {
        long expectedPrice = 70_000L;
        long currentPrice = 72_500L; // 70000 * 1.02 = 71400, 72500 > 71400

        when(stockPriceStore.get("005930")).thenReturn(mockPrice("005930", currentPrice));

        assertThatThrownBy(() -> tradeBuyProcessor.processBuy(1L, "test-key", new BuyReq(1L, 10L, expectedPrice)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("가격이 너무 올랐습니다");
    }

    @Test
    @DisplayName("슬리피지 허용 — 현재가가 예상가 대비 2% 이하 상승 시 정상 매수")
    void 슬리피지_허용_범위_내_매수() {
        long expectedPrice = 70_000L;
        long currentPrice = 71_000L; // 70000 * 1.02 = 71400, 71000 <= 71400

        when(stockPriceStore.get("005930")).thenReturn(mockPrice("005930", currentPrice));
        when(userStockRepository.findByUsersIdAndStockId(1L, 1L)).thenReturn(Optional.empty());

        BuyRes response = tradeBuyProcessor.processBuy(1L, "test-key", new BuyReq(1L, 10L, expectedPrice));

        assertThat(response.price()).isEqualTo(currentPrice);
    }

    @Test
    @DisplayName("가격 신선도 초과 — 웹소켓 체결시각 10초 초과 시 체결 거부")
    void 가격_신선도_초과_예외() {
        String staleTime = LocalDateTime.now().minusSeconds(11)
                .format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));

        when(stockPriceStore.get("005930")).thenReturn(
                RealtimeStockPrice.builder()
                        .stockCode("005930")
                        .price("70000")
                        .tradeTime(staleTime)
                        .build()
        );

        assertThatThrownBy(() -> tradeBuyProcessor.processBuy(1L, "test-key", new BuyReq(1L, 10L, 70_000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("가격 정보가 오래되었습니다");
    }

    @Test
    @DisplayName("가격 신선도 허용 — 웹소켓 체결시각 10초 이내 시 정상 매수")
    void 가격_신선도_허용_정상_매수() {
        String freshTime = LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"));

        when(stockPriceStore.get("005930")).thenReturn(
                RealtimeStockPrice.builder()
                        .stockCode("005930")
                        .price("70000")
                        .tradeTime(freshTime)
                        .build()
        );
        when(userStockRepository.findByUsersIdAndStockId(1L, 1L)).thenReturn(Optional.empty());

        BuyRes response = tradeBuyProcessor.processBuy(1L, "test-key", new BuyReq(1L, 10L, 70_000L));

        assertThat(response.price()).isEqualTo(70_000L);
    }

    @Test
    @DisplayName("현재가 없음 — 예외 발생")
    void 현재가_없을때_예외() {
        when(stockPriceStore.get("005930")).thenReturn(null);

        assertThatThrownBy(() -> tradeBuyProcessor.processBuy(1L, "test-key", new BuyReq(1L, 10L, 70_000L)))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("현재가 정보");
    }
}
