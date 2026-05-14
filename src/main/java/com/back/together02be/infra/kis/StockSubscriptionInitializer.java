package com.back.together02be.infra.kis;

import com.back.together02be.infra.kis.rest.KisPriceClient;
import com.back.together02be.infra.kis.websocket.KisWebSocketClient;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.infra.kis.rest.dto.KisPriceRes;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.repository.StockRepository;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockSubscriptionInitializer {

	private final StockRepository stockRepository;
	private final KisPriceClient kisPriceClient;
	private final KisWebSocketClient kisWebSocketClient;
	private final RealTimeStockPriceStore rtStockPriceStore;

	@EventListener(ApplicationReadyEvent.class)
	@Async
	public void initialize() {
		log.info("주식 종목 구독 초기화 시작");

		List<Stock> stocks = stockRepository.findAll();
		if (stocks.isEmpty()) {
			log.warn("등록된 주식 종목 없음, 초기화 중단");
			return;
		}

		seedPricesByRest(stocks);

		// stocks.forEach(stock -> {
		// 	RealtimeStockPrice cached = rtStockPriceStore.get(stock.getStockCode());
		// 	log.info("rest 캐시 확인 - {}: currentPrice={}, changeRate={}",
		// 		stock.getStockCode(),
		// 		cached != null ? cached.getPrice() : "null",
		// 		cached != null ? cached.getChangeRate() : "null");
		// });

		subscribeAll(stocks);

		log.info("주식 종목 구독 초기화 완료 ({}개 종목)",
			stocks.size());
	}

	private void seedPricesByRest(List<Stock> stocks) {
		String token = kisPriceClient.getAccessToken();
		int success = 0;
		int fail = 0;

		for (Stock stock : stocks) {
			try {
				KisPriceRes restStock = kisPriceClient.getCurrentPrice(token, stock.getStockCode());

				// 웹소켓이 채운 값은 덮어쓰지 않음
				rtStockPriceStore.putIfAbsent(
					stock.getStockCode(),
					RealtimeStockPrice.fromRest(stock.getStockCode(), restStock.output())
				);
				success++;

			} catch (Exception e) {
				fail++;
				log.warn("REST 주식 종목 구독 실패: {} - {} - {}", stock.getStockCode(), stock.getStockName(), e.getMessage());
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				log.warn("주식 종목 시딩 중단됨");
				return;
			}
		}
		log.info("REST 주식 종목 시딩 결과: 성공 {}, 실패 {}", success, fail);
	}

	private void subscribeAll(List<Stock> stocks) {
		for (Stock stock : stocks) {
			try {
				kisWebSocketClient.subscribe(stock.getStockCode());
			} catch (Exception e) {
				log.warn("주식 종목 구독 실패: {} - {} - {}", stock.getStockCode(), stock.getStockName(), e.getMessage());
			}
		}
	}
}