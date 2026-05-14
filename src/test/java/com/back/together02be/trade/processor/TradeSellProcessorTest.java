package com.back.together02be.trade.processor; // [추가] 패키지 선언

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserAccountRepository;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.stock.entity.StockMarket;
import com.back.together02be.trade.util.MarketTimeValidator;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.repository.StockRepository;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import com.back.together02be.trade.dto.request.TradeSellReq;
import com.back.together02be.trade.dto.response.TradeSellRes;
import com.back.together02be.trade.repository.TradeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeSellProcessorTest {

    @InjectMocks
    private TradeSellProcessor tradeSellProcessor;

    @Mock private RealTimeStockPriceStore stockPriceStore;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private UserStockRepository userStockRepository;
    @Mock private StockRepository stockRepository;
    @Mock private TradeRepository tradeRepository;

    private MockedStatic<MarketTimeValidator> marketValidator;

    @BeforeEach
    void setUp() {
        marketValidator = mockStatic(MarketTimeValidator.class);
    }

    @AfterEach
    void tearDown() {
        marketValidator.close();
    }

    // 공통 Mocking 설정을 위한 Helper 메서드 (코드 중복 제거)
    private void mockCommonDependencies(Stock stock, UserStock userStock, UserAccount account) {
        given(stockRepository.findById(any())).willReturn(Optional.of(stock));
        given(userStockRepository.findByUsersIdAndStockId(any(), any())).willReturn(Optional.of(userStock));
        given(userAccountRepository.findByUsersId(any())).willReturn(Optional.of(account));
    }

    // t1: 부분 매도 성공
    @Test
    @DisplayName("t1: 부분 매도 성공")
    void t1() {
        Stock stock = new Stock("005930", "삼성전자", StockMarket.KOSPI);
        UserStock userStock = new UserStock(null, stock, 20L, 10000L);
        UserAccount account = new UserAccount(null, 1000000L, 0L);
        mockCommonDependencies(stock, userStock, account);

        String nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        given(stockPriceStore.get(stock.getStockCode())).willReturn(
                RealtimeStockPrice.builder().price("55000").tradeTime(nowTime).build());
        given(userStockRepository.updateQuantity(any(), any(), any())).willReturn(1);

        TradeSellRes res = tradeSellProcessor.processSell(1L, new TradeSellReq(1L, 10L, 10L, 50000L));

        assertThat(res.quantity()).isEqualTo(10L);
        verify(tradeRepository).save(any());
    }

    // t2: 전량 매도 성공
    @Test
    @DisplayName("t2: 전량 매도 성공")
    void t2() {
        Stock stock = new Stock("005930", "삼성전자", StockMarket.KOSPI);
        UserStock userStock = new UserStock(null, stock, 20L, 10000L);
        UserAccount account = new UserAccount(null, 1000000L, 0L);
        mockCommonDependencies(stock, userStock, account);

        String nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        given(stockPriceStore.get(stock.getStockCode())).willReturn(
                RealtimeStockPrice.builder().price("55000").tradeTime(nowTime).build());
        given(userStockRepository.updateQuantity(any(), any(), any())).willReturn(1);

        tradeSellProcessor.processSell(1L, new TradeSellReq(1L, 10L, 20L, 50000L));

        verify(userStockRepository).deleteByUserAndStock(1L, 10L);
    }

    // t3: 실패 - 가격 변동폭 초과
    @Test
    @DisplayName("t3: 실패 - 가격 변동폭 초과")
    void t3() {
        Stock stock = new Stock("005930", "삼성전자", StockMarket.KOSPI);
        UserStock userStock = new UserStock(null, stock, 20L, 10000L);
        UserAccount account = new UserAccount(null, 1000000L, 0L);
        mockCommonDependencies(stock, userStock, account); // 필수!!

        String nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        given(stockPriceStore.get(stock.getStockCode())).willReturn(
                RealtimeStockPrice.builder().price("55000").tradeTime(nowTime).build());

        assertThrows(IllegalStateException.class, () ->
                tradeSellProcessor.processSell(1L, new TradeSellReq(1L, 10L, 10L, 100000L)));
    }

    // t4: 실패 - 보유 수량 부족
    @Test
    @DisplayName("t4: 실패 - 보유 수량 부족")
    void t4() {
        Stock stock = new Stock("005930", "삼성전자", StockMarket.KOSPI);
        UserStock userStock = new UserStock(null, stock, 20L, 10000L);
        UserAccount account = new UserAccount(null, 1000000L, 0L);
        mockCommonDependencies(stock, userStock, account); // 필수!!

        // [추가] 가격 정보가 정상적으로 들어오도록 설정
        String nowTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        given(stockPriceStore.get(any())).willReturn(
                RealtimeStockPrice.builder().price("55000").tradeTime(nowTime).build());
        given(userStockRepository.updateQuantity(any(), any(), any())).willReturn(0);

        assertThrows(IllegalStateException.class, () ->
                tradeSellProcessor.processSell(1L, new TradeSellReq(1L, 10L, 100L, 10000L)));
    }
}