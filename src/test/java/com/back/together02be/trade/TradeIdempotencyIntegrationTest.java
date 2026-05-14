package com.back.together02be.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.repository.UserAccountRepository;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.global.idempotency.IdempotencyKeyRepository;
import com.back.together02be.achievement.listener.AchievementEventListener;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.entity.StockMarket;
import com.back.together02be.stock.repository.StockRepository;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import com.back.together02be.trade.dto.BuyReq;
import com.back.together02be.trade.dto.BuyRes;
import com.back.together02be.trade.repository.TradeRepository;
import com.back.together02be.trade.service.TradeService;
import com.back.together02be.users.entity.Users;
import com.back.together02be.users.repository.UsersRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 멱등성 키 통합 테스트 — 실제 DB를 사용해 네트워크 재전송 방어를 검증한다.
 * @Transactional 미사용 — 멱등성 키 UNIQUE 제약은 커밋 후에만 다른 스레드가 감지할 수 있다.
 */
@SpringBootTest
class TradeIdempotencyIntegrationTest {

    @Autowired TradeService tradeService;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired UserStockRepository userStockRepository;
    @Autowired UsersRepository usersRepository;
    @Autowired StockRepository stockRepository;
    @Autowired TradeRepository tradeRepository;
    @Autowired IdempotencyKeyRepository idempotencyKeyRepository;

    @MockitoBean RealTimeStockPriceStore stockPriceStore;
    @MockitoBean AchievementEventListener achievementEventListener;

    private Users user;
    private Stock stock;

    @BeforeEach
    void setUp() {
        user = usersRepository.save(new Users("idempotency_user", "pw", "멱등성테스트유저"));
        stock = stockRepository.save(new Stock("888888", "멱등성테스트종목", StockMarket.KOSPI));
        userAccountRepository.save(new UserAccount(user, 0L, 10_000_000L));

        when(stockPriceStore.get("888888")).thenReturn(
                RealtimeStockPrice.builder()
                        .stockCode("888888")
                        .price("70000")
                        .build()
        );
    }

    @AfterEach
    void tearDown() {
        tradeRepository.deleteAll(
                tradeRepository.findAll().stream()
                        .filter(t -> t.getStock().getId().equals(stock.getId()))
                        .toList()
        );
        userStockRepository.findByUsersIdAndStockId(user.getId(), stock.getId())
                .ifPresent(userStockRepository::delete);
        userAccountRepository.findByUsersId(user.getId())
                .ifPresent(userAccountRepository::delete);
        idempotencyKeyRepository.deleteAll(
                idempotencyKeyRepository.findAll().stream()
                        .filter(k -> k.getUserId().equals(user.getId()))
                        .toList()
        );
        stockRepository.delete(stock);
        usersRepository.delete(user);
    }

    @Test
    @DisplayName("동일 멱등성 키 동시 5회 재전송 — DB Trade 체결은 1건만")
    void 동일_키_동시_재전송_체결_1건() throws InterruptedException {
        String idempotencyKey = UUID.randomUUID().toString();
        BuyReq request = new BuyReq(stock.getId(), 10L, 70_000L);

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger blockedCount = new AtomicInteger(0);
        List<Long> tradeIds = new CopyOnWriteArrayList<>();

        System.out.println("=".repeat(60));
        System.out.println("[멱등성 테스트 1] 동일 키로 " + threadCount + "개 스레드 동시 전송");
        System.out.println("키: " + idempotencyKey);
        System.out.println("=".repeat(60));

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i + 1;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    BuyRes res = tradeService.buy(user.getId(), idempotencyKey, request);
                    successCount.incrementAndGet();
                    tradeIds.add(res.tradeId());
                    System.out.printf("[스레드-%d] 응답 수신 — tradeId=%d%n", threadId, res.tradeId());
                } catch (Exception e) {
                    blockedCount.incrementAndGet();
                    System.out.printf("[스레드-%d] 처리 중 차단 — %s%n", threadId, e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        long tradeCount = tradeRepository.findAll().stream()
                .filter(t -> t.getStock().getId().equals(stock.getId()))
                .count();
        long distinctTradeIds = tradeIds.stream().distinct().count();

        System.out.println("=".repeat(60));
        System.out.printf("[결과] 응답 수신: %d건 | 처리 중 차단: %d건%n", successCount.get(), blockedCount.get());
        System.out.printf("[결과] 수신된 tradeId 목록: %s (모두 동일해야 함)%n", tradeIds);
        System.out.printf("[결과] DB Trade 저장: %d건 (기대값 1건 — 중복 체결 없음)%n", tradeCount);
        System.out.println("=".repeat(60));

        // 핵심: 몇 건이 성공하든 DB에는 Trade 1건, 모든 응답이 같은 tradeId
        assertThat(tradeCount).isEqualTo(1);
        assertThat(distinctTradeIds).isEqualTo(1);
    }

    @Test
    @DisplayName("완료된 요청 재전송 — 캐시된 응답 반환, DB 체결 추가 없음")
    void 완료된_요청_재전송_캐시_응답() {
        String idempotencyKey = UUID.randomUUID().toString();
        BuyReq request = new BuyReq(stock.getId(), 10L, 70_000L);

        System.out.println("=".repeat(60));
        System.out.println("[멱등성 테스트 2] 완료 후 동일 키 순차 재전송");
        System.out.println("=".repeat(60));

        // 1차 요청 — 정상 체결
        BuyRes first = tradeService.buy(user.getId(), idempotencyKey, request);
        System.out.printf("[1차] 체결 완료 — tradeId=%d, 금액=%,d원%n", first.tradeId(), first.amount());

        long countAfterFirst = tradeRepository.findAll().stream()
                .filter(t -> t.getStock().getId().equals(stock.getId()))
                .count();
        System.out.printf("[1차 후] DB Trade: %d건%n", countAfterFirst);

        // 2차 요청 — 네트워크 재전송 시뮬레이션
        BuyRes second = tradeService.buy(user.getId(), idempotencyKey, request);
        System.out.printf("[2차] 캐시 응답 반환 — tradeId=%d (1차와 동일)%n", second.tradeId());

        long countAfterSecond = tradeRepository.findAll().stream()
                .filter(t -> t.getStock().getId().equals(stock.getId()))
                .count();

        System.out.println("=".repeat(60));
        System.out.printf("[결과] 1차 tradeId=%d | 2차 tradeId=%d → %s%n",
                first.tradeId(), second.tradeId(),
                first.tradeId().equals(second.tradeId()) ? "동일 (캐시 응답)" : "다름 (버그!)");
        System.out.printf("[결과] DB Trade: %d건 (기대값 1건 — 재전송으로 중복 체결 없음)%n", countAfterSecond);
        System.out.println("=".repeat(60));

        assertThat(first.tradeId()).isEqualTo(second.tradeId());
        assertThat(countAfterSecond).isEqualTo(1);
    }
}
