package com.back.together02be.infra.kis;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.back.together02be.infra.kis.rest.KisPriceClient;
import com.back.together02be.infra.kis.rest.dto.KisPriceRes;
import com.back.together02be.infra.kis.websocket.KisWebSocketClient;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.entity.StockMarket;
import com.back.together02be.stock.repository.StockRepository;
import com.back.together02be.stock.service.RealTimeStockPriceStore;

/**
 * StockSubscriptionInitializer 특성화 테스트 (Characterization Test)
 *
 * 목적: 복구 로직 추가 이전에 현재 동작을 고정한다.
 *
 * 전략: 순수 단위 테스트. Spring 컨텍스트 없이 수동 생성.
 *       @EventListener + @Async는 테스트 스코프 밖 — initialize()를 직접 호출.
 *
 * 주의: seedPricesByRest()에 Thread.sleep(1000)이 종목당 1번 박혀있음.
 *       종목 수를 최소화(1~2개)해서 sleep 총량을 줄임.
 *       테스트 타임아웃은 메서드당 10초로 설정.
 */
class StockSubscriptionInitializerTest {

	// Mock

	private StockRepository stockRepository;
	private KisPriceClient kisPriceClient;
	private KisWebSocketClient kisWebSocketClient;
	private RealTimeStockPriceStore rtStockPriceStore;

	// SUT

	private StockSubscriptionInitializer sut;

	// 테스트 픽스처

	private static final String TOKEN = "test-token";

	private Stock stock(String code) {
		return new Stock(code, code + "_name", StockMarket.KOSPI);
	}

	// KisPriceRes mock: output()이 null이면 RealtimeStockPrice.fromRest()에서 NPE 가능.
	// output()을 mock으로 stub해서 방어한다.
	private KisPriceRes mockPriceRes() {
		KisPriceRes.Output output = new KisPriceRes.Output(
			"70800",   // currentPrice
			"100",     // priceDifference
			"2",       // changeSign
			"0.14"     // changeRate
		);
		return new KisPriceRes("0", "MCA00000", "정상처리", output);
	}

	// Lifecycle

	@BeforeEach
	void setUp() {
		stockRepository    = mock(StockRepository.class);
		kisPriceClient     = mock(KisPriceClient.class);
		kisWebSocketClient = mock(KisWebSocketClient.class);
		rtStockPriceStore  = mock(RealTimeStockPriceStore.class);

		when(kisPriceClient.getAccessToken()).thenReturn(TOKEN);

		sut = new StockSubscriptionInitializer(
			stockRepository, kisPriceClient, kisWebSocketClient, rtStockPriceStore
		);
	}

	// 테스트 케이스

	@Test
	@DisplayName("종목이 없으면 조기 리턴 — 후속 REST 호출 없음")
	void initialize_whenNoStocks_returnsEarly() throws InterruptedException {
		when(stockRepository.findAll()).thenReturn(List.of());

		sut.initialize();

		verify(kisPriceClient, never()).getAccessToken();
		verify(kisPriceClient, never()).getCurrentPrice(anyString(), anyString());
		verify(kisWebSocketClient, never()).subscribe(anyString());
		verify(rtStockPriceStore, never()).putIfAbsent(anyString(), any());
	}

	@Test
	@DisplayName("종목이 있으면 REST 시딩 후 WebSocket 구독이 호출된다 (1종목, sleep 1초)")
	void initialize_withOneStock_seedsAndSubscribes() throws InterruptedException {
		Stock s = stock("005930");
		when(stockRepository.findAll()).thenReturn(List.of(s));
		when(kisPriceClient.getCurrentPrice(TOKEN, "005930")).thenReturn(mockPriceRes());

		sut.initialize();

		verify(kisPriceClient).getCurrentPrice(TOKEN, "005930");
		verify(rtStockPriceStore).putIfAbsent(eq("005930"), any(RealtimeStockPrice.class));
		verify(kisWebSocketClient).subscribe("005930");
	}

	@Test
	@DisplayName("시딩 루프 전체 완료 후 구독 루프가 시작된다 — InOrder로 순서 박제 (2종목, sleep 2초)")
	void initialize_seedingCompletesBeforeSubscribing() throws InterruptedException {
		Stock s1 = stock("005930");
		Stock s2 = stock("000660");
		when(stockRepository.findAll()).thenReturn(List.of(s1, s2));
		when(kisPriceClient.getCurrentPrice(eq(TOKEN), anyString())).thenReturn(mockPriceRes());

		sut.initialize();

		// 시딩(putIfAbsent) → 구독(subscribe) 순서 검증
		InOrder inOrder = inOrder(rtStockPriceStore, kisWebSocketClient);
		inOrder.verify(rtStockPriceStore, times(2)).putIfAbsent(anyString(), any());
		inOrder.verify(kisWebSocketClient, times(2)).subscribe(anyString());
	}

	@Test
	@DisplayName("REST 시딩 실패한 종목이 있어도 나머지 종목은 계속 처리된다 (2종목, sleep 2초)")
	void seedPrices_whenOneStockFails_othersAreProcessed() throws InterruptedException {
		Stock s1 = stock("005930");
		Stock s2 = stock("000660");
		when(stockRepository.findAll()).thenReturn(List.of(s1, s2));

		// 005930 실패, 000660 성공
		when(kisPriceClient.getCurrentPrice(TOKEN, "005930"))
			.thenThrow(new RuntimeException("REST 오류"));
		when(kisPriceClient.getCurrentPrice(TOKEN, "000660"))
			.thenReturn(mockPriceRes());

		sut.initialize();

		// 005930은 실패했으므로 putIfAbsent 호출 안 됨
		verify(rtStockPriceStore, never()).putIfAbsent(eq("005930"), any());
		// 000660은 성공
		verify(rtStockPriceStore).putIfAbsent(eq("000660"), any(RealtimeStockPrice.class));

		// 구독은 실패 여부와 무관하게 전 종목 시도
		verify(kisWebSocketClient).subscribe("005930");
		verify(kisWebSocketClient).subscribe("000660");
	}

	@Test
	@DisplayName("WebSocket 구독 실패한 종목이 있어도 나머지 종목 구독은 계속 시도된다 (2종목, sleep 2초)")
	void subscribeAll_whenOneStockFails_othersAreSubscribed() throws InterruptedException {
		Stock s1 = stock("005930");
		Stock s2 = stock("000660");
		when(stockRepository.findAll()).thenReturn(List.of(s1, s2));
		when(kisPriceClient.getCurrentPrice(eq(TOKEN), anyString())).thenReturn(mockPriceRes());

		// 005930 구독 실패
		doThrow(new RuntimeException("구독 오류")).when(kisWebSocketClient).subscribe("005930");

		sut.initialize();

		// 실패해도 000660 구독은 계속 시도
		verify(kisWebSocketClient).subscribe("005930");
		verify(kisWebSocketClient).subscribe("000660");
	}

	@Test
	@DisplayName("캐시 저장은 putIfAbsent로 호출된다 — put은 호출되지 않는다 (1종목, sleep 1초)")
	void seedPrices_usesPutIfAbsent_notPut() throws InterruptedException {
		Stock s = stock("005930");
		when(stockRepository.findAll()).thenReturn(List.of(s));
		when(kisPriceClient.getCurrentPrice(TOKEN, "005930")).thenReturn(mockPriceRes());

		sut.initialize();

		verify(rtStockPriceStore).putIfAbsent(eq("005930"), any(RealtimeStockPrice.class));
		verify(rtStockPriceStore, never()).put(anyString(), any());
	}
}