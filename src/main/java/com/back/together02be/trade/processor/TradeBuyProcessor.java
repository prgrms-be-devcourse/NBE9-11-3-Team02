package com.back.together02be.trade.processor;

import com.back.together02be.achievement.event.TradeCompletedEvent;
import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserAccountRepository;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.global.idempotency.IdempotencyKeyRepository;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.repository.StockRepository;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import com.back.together02be.trade.dto.BuyReq;
import com.back.together02be.trade.dto.BuyRes;
import com.back.together02be.trade.entity.Trade;
import com.back.together02be.trade.repository.TradeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매수 트랜잭션 처리 전담 컴포넌트.
 * TradeService와 분리된 별도 빈으로 존재해야 Spring AOP 프록시를 통해 @Transactional이 정상 동작한다.
 */
@Component
@RequiredArgsConstructor
public class TradeBuyProcessor {

    private final RealTimeStockPriceStore stockPriceStore;
    private final UserAccountRepository userAccountRepository;
    private final UserStockRepository userStockRepository;
    private final StockRepository stockRepository;
    private final TradeRepository tradeRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher; // 추가

    @Transactional
    public BuyRes processBuy(Long userId, String idempotencyKey, BuyReq request) {
        // 1. 주식 정보 조회
        Stock stock = stockRepository.findById(request.stockId())
                .orElseThrow(() -> new EntityNotFoundException("주식 정보가 없습니다."));

        // 2. 현재가 조회 — KIS WebSocket 수신 후 RealTimeStockPriceStore에 저장된 실시간 가격
        RealtimeStockPrice stockPrice = stockPriceStore.get(stock.getStockCode());
        if (stockPrice == null) {
            throw new EntityNotFoundException("현재가 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
        Long price = Long.parseLong(stockPrice.getPrice());

        // 3. 가격 신선도 검증 — 웹소켓 체결시각 기준 10초 초과 시 stale 가격으로 판단해 거부
        String tradeTime = stockPrice.getTradeTime();
        if (tradeTime != null && !tradeTime.isEmpty()) {
            LocalDateTime tradeDateTime = LocalDateTime.of(
                    LocalDate.now(),
                    LocalTime.parse(tradeTime, DateTimeFormatter.ofPattern("HHmmss"))
            );
            if (Duration.between(tradeDateTime, LocalDateTime.now()).getSeconds() > 10) {
                throw new IllegalStateException("가격 정보가 오래되었습니다. 잠시 후 다시 시도해주세요.");
            }
        }

        // 4. 슬리피지 보호 — 현재가가 예상가 대비 2% 초과 상승 시 매수 거부
        if (price > request.expectedPrice() * 1.02) {
            throw new IllegalStateException(
                    String.format("가격이 너무 올랐습니다. (예상: %,d원 / 현재: %,d원)", request.expectedPrice(), price));
        }

        long amount = price * request.quantity();

        // 5. 원자적 잔고 차감 — 잔고 확인과 차감을 DB 단에서 한 번에 처리
        int updated = userAccountRepository.decreaseDepositIfSufficient(userId, amount);
        if (updated == 0) {
            UserAccount accountForMsg = userAccountRepository.findByUsersId(userId)
                    .orElseThrow(() -> new EntityNotFoundException("계좌 정보가 없습니다."));
            throw new IllegalStateException(
                    String.format("잔고가 부족합니다. (필요: %,d원 / 보유: %,d원)", amount, accountForMsg.getDeposit())
            );
        }

        // 5. 계좌 조회 + 비관적 락 — 이후 UserStock 처리 구간을 직렬화
        UserAccount account = userAccountRepository.findByUsersIdWithLock(userId)
                .orElseThrow(() -> new EntityNotFoundException("계좌 정보가 없습니다."));

        // 6. 보유 주식 업데이트 — 신규/추가 매수에 따라 분기
        UserStock userStock = userStockRepository
                .findByUsersIdAndStockId(userId, request.stockId())
                .orElse(null);

        if (userStock == null) {
            userStock = new UserStock(account.getUsers(), stock, request.quantity(), price);
            userStockRepository.save(userStock);
        } else {
            // 평균매입가 = (기존보유금액 + 신규매수금액) / 총수량
            userStock.updateOnBuy(request.quantity(), price);
        }

        // 6. 거래 내역 저장
        Trade trade = Trade.buy(account.getUsers(), stock, request.quantity(), price);
        tradeRepository.save(trade);

        eventPublisher.publishEvent(new TradeCompletedEvent(
                userId,
                amount,
                account.getTotalPurchase()
        ));

        BuyRes result = new BuyRes(
                trade.getId(),
                stock.getStockName(),
                request.quantity(),
                price,
                amount,
                account.getDeposit()
        );

        // 7. 응답을 같은 트랜잭션 안에서 저장 — 거래와 캐시가 원자적으로 커밋
        idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(k -> k.storeResponse(toJson(result)));

        return result;
    }

    @SneakyThrows
    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
