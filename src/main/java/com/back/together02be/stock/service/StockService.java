package com.back.together02be.stock.service;

import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.dto.response.StockListRes;
import com.back.together02be.stock.dto.response.StockPriceRes;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.repository.StockRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

	private final StockRepository stockRepository;

	// 전체 종목/상세 종목 조회에서 공통으로 사용하는 실시간 시세 캐시 저장소
 	private final RealTimeStockPriceStore rtStockPriceStore;

	private static final long LIST_SSE_INTERVAL_MS = 1500; //전체 종목 시세 갱신 주기
	private static final long SSE_INTERVAL_MS = 500; // 상세 종목 시세 갱신 주기


	// 전체 종목 조회 시 DB의 종목 기본 정보와 실시간 시세 캐시를 조합해 응답을 생성한다.
	public List<StockListRes> getStocks() {
        List<StockListRes> result = new ArrayList<>();

		for (Stock stock : stockRepository.findAll()) {
			RealtimeStockPrice cached = rtStockPriceStore.get(stock.getStockCode());

			Long currentPrice = null;
			Double changeRate = null;

			if (cached != null) {
				currentPrice = parseLongSafely(cached.getPrice());
				changeRate = parseDoubleSafely(cached.getChangeRate());
			}

			result.add(new StockListRes(
				stock.getId(),
				stock.getStockCode(),
				stock.getStockName(),
				currentPrice,
				changeRate
			));
		}

		return result;
	}

	// 문자열 가격 값을 Long으로 안전하게 변환하고, 값이 없거나 형식이 잘못되면 null을 반환한다.
	private Long parseLongSafely(String value) {
		try {
			return value == null || value.isBlank() ? null : Long.parseLong(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// 문자열 등락률 값을 Double로 안전하게 변환하고, 값이 없거나 형식이 잘못되면 null을 반환한다.
	private Double parseDoubleSafely(String value) {
		try {
			return value == null || value.isBlank() ? null : Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	//전체 종목 실시간 정보를 1.5초 주기로 SSE 전송
	public SseEmitter createStockListSseEmitter() {
		SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		executor.scheduleAtFixedRate(() -> {
			try {
				emitter.send(getStocks());
			} catch (IOException e) {
				emitter.complete();
				executor.shutdown();
			} catch (Exception e) {
				log.error("전체 종목 SSE 오류: {}", e.getMessage(), e);
				emitter.complete();
				executor.shutdown();
			}
		}, 0, LIST_SSE_INTERVAL_MS, TimeUnit.MILLISECONDS);

		emitter.onCompletion(executor::shutdown);
		emitter.onTimeout(() -> {
			executor.shutdown();
			log.info("전체 종목 SSE 타임아웃");
		});

		return emitter;
	}

	public SseEmitter createSseEmitter(String stockCode) {

		findStock(stockCode);

		SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
		executor.scheduleAtFixedRate(() -> { // (작업, 초기 지연, 주기, 단위)
			try {
				RealtimeStockPrice stockPrice = rtStockPriceStore.get(stockCode);
				if (stockPrice != null) {
					emitter.send(stockPrice);
				}

			} catch (IOException e) {
				emitter.complete();
				executor.shutdown();
			} catch (Exception e) {
				log.error("SSE 오류 - 종목: {} | 원인: {}", stockCode, e.getMessage(), e);
				emitter.complete();
				executor.shutdown();
			}
		}, 0, SSE_INTERVAL_MS, TimeUnit.MILLISECONDS);

		emitter.onCompletion(() -> executor.shutdown());

		emitter.onTimeout(() -> {
			executor.shutdown();
			log.info("SSE 타임아웃 - 종목: {}", stockCode);
		});

		return emitter;
	}

	public StockPriceRes getStockPrice(String stockCode) {
		return StockPriceRes.from(findStock(stockCode));
	}

	private Stock findStock(String stockCode) {
		return stockRepository.findByStockCode(stockCode)
			.orElseThrow(() -> new EntityNotFoundException("존재하지 않는 종목코드입니다: " + stockCode));
	}
}
