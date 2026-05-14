package com.back.together02be.asset.service;

import com.back.together02be.asset.dto.response.StockInfoRes;
import com.back.together02be.asset.dto.response.UserStockRes;
import com.back.together02be.asset.entity.UserAccount;
import com.back.together02be.asset.entity.UserStock;
import com.back.together02be.asset.repository.UserAccountRepository;
import com.back.together02be.asset.repository.UserStockRepository;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class AssetService {
    private final UserAccountRepository userAccountRepository;
    private final UserStockRepository userStockRepository;
    private final RealTimeStockPriceStore realTimeStockPriceStore;
    private final UserStockSseService userStockSseService;

    //예수금 조회 메서드
    public long getDeposit(Long userId){
        UserAccount userAccount = userAccountRepository.findByUsersId(userId)
                .orElseThrow(() -> new IllegalArgumentException("계좌 없음"));
        return userAccount.getDeposit();
    }

    // 보유 종목 조회 메서드
    public List<UserStockRes> getUserStocks(Long userId) {
        //보유 주식 목록 조회
        List<UserStock> userStocks = userStockRepository.findAllByUsersId(userId);
        return getUserStocksRealtimePrice(userStocks);
    }

    // 유저 보유 종목 현재 시세 조회
    public List<UserStockRes> getUserStocksRealtimePrice(List<UserStock> userStocks) {

        return userStocks.stream().map(userStock -> {
            String stockCode = userStock.getStock().getStockCode();
            // RealTimeStockPriceStore의 get 메서드를 사용하여 실시간 시세 조회
            RealtimeStockPrice realtimeStockPrice = realTimeStockPriceStore.get(stockCode);

            // Map에 아직 데이터가 적재되지 않아 null을 반환할 경우를 대비한 방어 로직
            Long currentPrice = (realtimeStockPrice != null) ? Long.parseLong(realtimeStockPrice.getPrice()) : 0L;


            return UserStockRes.from(userStock, currentPrice);
        }).collect(Collectors.toList());
    }
    public long getTotalAmountByUserId(long userId){
        return userAccountRepository.findByUsersId(userId)
                .orElseThrow(()->new RuntimeException("계좌 없음"))
                .getTotalPurchase();
    }
    public List<StockInfoRes> getStockInfo(long userId){
        List<UserStock> userStocks = userStockRepository.findAllByUsersId(userId);

        List<StockInfoRes> stockInfos = userStocks.stream()
                .map(us->new StockInfoRes(us.getStock().getStockCode(),us.getQuantity()))
                .toList();

        return stockInfos;
    }

    // SSE 다중 종목 구독
    public SseEmitter subscribeToUserStocks(Long userId) {
        List<UserStock> userStocks = userStockRepository.findAllByUsersId(userId);
        List<String> stockCodes = userStocks.stream().map(s->s.getStock().getStockCode()).toList();

        SseEmitter emitter = userStockSseService.createEmitter();

        // 💡 만약 보유 주식이 없다면, 503 에러를 막기 위해 연결 더미 데이터만 보내고 유지합니다.
        if (stockCodes.isEmpty()) {
            try {
                emitter.send(SseEmitter.event().name("CONNECT").data("no_stocks"));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        stockCodes.forEach(code -> userStockSseService.addEmitter(code, emitter));

        Runnable onCompletion = () -> {
            stockCodes.forEach(code -> userStockSseService.removeEmitter(code, emitter));
        };

        emitter.onCompletion(onCompletion);
        emitter.onTimeout(onCompletion);
        emitter.onError((e) -> onCompletion.run());

        // 💡 503 에러 방지용 첫 이벤트 전송
        try {
            emitter.send(SseEmitter.event().name("CONNECT").data("connected"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }
}
