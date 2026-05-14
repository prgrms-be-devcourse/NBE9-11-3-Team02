package com.back.together02be.trade;

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserAccountRepository;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import com.back.together02be.trade.controller.TradeController;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
public class TradeControllerSellTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserStockRepository userStockRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RealTimeStockPriceStore realtimeStockPriceService;

    // ────────────────────────────────────────────
    // 성공 케이스
    // ────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // 테스트용 실시간 가격 데이터 주입 (삼성전자 등)
        RealtimeStockPrice samsungPrice = RealtimeStockPrice.builder()
                .stockCode("005930")
                .price("75000")
                .changeSign("1")
                .change("1")
                .changeRate("3")
                .tradeTime("17:05")
                .build();
        realtimeStockPriceService.put("005930", samsungPrice);
    }

    @Test
    @DisplayName("매도 성공 - 부분 매도")
    void t1() throws Exception {

        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "userId": 1,
                                            "stockId": 1,
                                            "quantity": 5
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(handler().handlerType(TradeController.class))
                .andExpect(handler().methodName("sell"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("매도 주문이 성공적으로 체결되었습니다."))
                .andExpect(jsonPath("$.data").value("SUCCESS"));

        // DB 상태 검증
        UserStock userStock = userStockRepository
                .findByUsersIdAndStockId(1L, 1L)
                .orElseThrow();
        UserAccount userAccount = userAccountRepository
                .findByUsersId(1L)
                .orElseThrow();

        assertThat(userStock.getQuantity()).isEqualTo(5L);              // 10 - 5
        assertThat(userAccount.getDeposit()).isEqualTo(1375000L);       // 100만 + 75000*5
        assertThat(userAccount.getTotalPurchase()).isEqualTo(350000L);  // 70만 - 70000*5
    }

    @Test
    @DisplayName("매도 성공 - 전량 매도 시 UserStock 삭제")
    void t2() throws Exception {
        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "userId": 1,
                                            "stockId": 1,
                                            "quantity": 10
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(handler().handlerType(TradeController.class))
                .andExpect(handler().methodName("sell"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("매도 주문이 성공적으로 체결되었습니다."))
                .andExpect(jsonPath("$.data").value("SUCCESS"));

        // DB 상태 검증 - UserStock 삭제 확인
        Optional<UserStock> deleted = userStockRepository
                .findByUsersIdAndStockId(1L, 1L);
        UserAccount userAccount = userAccountRepository
                .findByUsersId(1L)
                .orElseThrow();

        assertThat(deleted).isEmpty();                                  // 전량매도 → 삭제
        assertThat(userAccount.getDeposit()).isEqualTo(1750000L);       // 100만 + 75000*10
        assertThat(userAccount.getTotalPurchase()).isEqualTo(0L);       // 70만 - 70000*10
    }

    @Test
    @DisplayName("매도 성공 - 손실 매도 (현재가 < 평단가)")
    void t3() throws Exception {
        RealtimeStockPrice lossPrice = RealtimeStockPrice.builder()
                .stockCode("005930")
                .price("60000")
                .build();
        realtimeStockPriceService.put("005930", lossPrice);

        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "userId": 1,
                                            "stockId": 1,
                                            "quantity": 1
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("매도 주문이 성공적으로 체결되었습니다."));

        // profit = (60000 - 70000) * 1 = -10000 → Trade에 저장됐는지 확인
        UserStock userStock = userStockRepository
                .findByUsersIdAndStockId(1L, 1L)
                .orElseThrow();

        assertThat(userStock.getQuantity()).isEqualTo(9L);              // 10 - 1
        assertThat(userAccount().getDeposit()).isEqualTo(1060000L);     // 100만 + 60000*1
    }

    // ────────────────────────────────────────────
    // 실패 케이스
    // ────────────────────────────────────────────

    @Test
    @DisplayName("매도 실패 - 보유하지 않은 종목")
    void t4() throws Exception {
        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "userId": 1,
                                            "stockId": 999,
                                            "quantity": 1,
                                            "price": 75000
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(handler().handlerType(TradeController.class))
                .andExpect(handler().methodName("sell"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("보유하지 않은 주식입니다."));
    }

    @Test
    @DisplayName("매도 실패 - 보유 수량 초과")
    void t5() throws Exception {
        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "userId": 1,
                                            "stockId": 1,
                                            "quantity": 11,
                                            "price": 75000
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(handler().handlerType(TradeController.class))
                .andExpect(handler().methodName("sell"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("보유 수량이 부족합니다."));

        // DB 수량 변화 없음 검증
        UserStock userStock = userStockRepository
                .findByUsersIdAndStockId(1L, 1L)
                .orElseThrow();

        assertThat(userStock.getQuantity()).isEqualTo(10L); // 그대로
    }

    @Test
    @DisplayName("매도 실패 - quantity가 0 이하")
    void t6() throws Exception {
        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "userId": 1,
                                            "stockId": 1,
                                            "quantity": 0,
                                            "price": 75000
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(handler().handlerType(TradeController.class))
                .andExpect(handler().methodName("sell"))
                .andExpect(status().isBadRequest());
    }

    /*
    @Test
    @DisplayName("매도 실패 - price가 0 이하")
    void t7() throws Exception {
        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "userId": 1,
                                            "stockId": 1,
                                            "quantity": 1,
                                            "price": 0
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(handler().handlerType(TradeController.class))
                .andExpect(handler().methodName("sell"))
                .andExpect(status().isBadRequest());
    }
    */

    @Test
    @DisplayName("매도 실패 - 존재하지 않는 유저")
    void t8() throws Exception {
        ResultActions result = mvc
                .perform(
                        post("/api/trades/sell")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "userId": 999,
                                            "stockId": 1,
                                            "quantity": 1,
                                            "price": 75000
                                        }
                                        """)
                )
                .andDo(print());

        result
                .andExpect(handler().handlerType(TradeController.class))
                .andExpect(handler().methodName("sell"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("보유하지 않은 주식입니다."));
    }

    // ────────────────────────────────────────────
    // 헬퍼
    // ────────────────────────────────────────────

    private UserAccount userAccount() {
        return userAccountRepository.findByUsersId(1L).orElseThrow();
    }
}
