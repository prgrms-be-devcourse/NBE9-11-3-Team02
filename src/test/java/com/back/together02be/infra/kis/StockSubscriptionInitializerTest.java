package com.back.together02be.infra.kis;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.InOrder;

import com.back.together02be.infra.kis.event.WebSocketReconnectedEvent;
import com.back.together02be.infra.kis.rest.KisPriceClient;
import com.back.together02be.infra.kis.rest.dto.KisPriceRes;
import com.back.together02be.infra.kis.websocket.KisWebSocketClient;
import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.entity.Stock;
import com.back.together02be.stock.entity.StockMarket;
import com.back.together02be.stock.repository.StockRepository;
import com.back.together02be.stock.service.RealTimeStockPriceStore;

/**
 * StockSubscriptionInitializer 단위 테스트
 * <p>
 * 다루는 흐름:
 * - initialize(): 앱 시작 시 REST 시딩 + WebSocket 구독
 * - onWebSocketReconnected(): WS 재연결 이벤트 수신 시 REST 재시딩
 * <p>
 * 전략: 순수 단위 테스트. Spring 컨텍스트 없이 수동 생성.
 *
 * @EventListener + @Async는 테스트 스코프 밖 — 메서드 직접 호출.
 * <p>
 * 주의: seedPricesByRest()와 reseedPricesByRest()에 Thread.sleep(1000)이 종목당 1번 박혀있음.
 * 종목 수를 최소화(1~2개)해서 sleep 총량을 줄임.
 */
@Timeout(value = 15, unit = TimeUnit.SECONDS)
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

	// Lifecycle
	@BeforeEach
	void setUp() {
		stockRepository = mock(StockRepository.class);
		kisPriceClient = mock(KisPriceClient.class);
		kisWebSocketClient = mock(KisWebSocketClient.class);
		rtStockPriceStore = mock(RealTimeStockPriceStore.class);

		when(kisPriceClient.getAccessToken()).thenReturn(TOKEN);

		sut = new StockSubscriptionInitializer(
			stockRepository, kisPriceClient, kisWebSocketClient, rtStockPriceStore
		);
	}

	// 헬퍼
	private Stock stock(String code) {
		return new Stock(code, code + "_name", StockMarket.KOSPI);
	}

	private KisPriceRes mockPriceRes() {
		KisPriceRes.Output output = new KisPriceRes.Output(
			"70800",   // currentPrice
			"100",     // priceDifference
			"2",       // changeSign
			"0.14"     // changeRate
		);
		return new KisPriceRes("0", "MCA00000", "정상처리", output);
	}

	private void setReseedInProgress(boolean value) throws Exception {
		var field = StockSubscriptionInitializer.class.getDeclaredField("reseedInProgress");
		field.setAccessible(true);
		AtomicBoolean flag = (AtomicBoolean)field.get(sut);
		flag.set(value);
	}

	// 테스트 케이스

	@Nested
	@DisplayName("앱 시작 시 초기화 동작")
	class InitializationTest {
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

	@Nested
	@DisplayName("WebSocket 재연결 후 재시딩 동작")
	class ReseedTest {

		@Test
		@DisplayName("재시딩은 put을 사용한다 — putIfAbsent는 호출되지 않는다 (1종목, sleep 1초)")
		void reseed_usesPut_notPutIfAbsent() throws InterruptedException {
			Stock s = stock("005930");
			when(stockRepository.findAll()).thenReturn(List.of(s));
			when(kisPriceClient.getCurrentPrice(TOKEN, "005930")).thenReturn(mockPriceRes());

			sut.onWebSocketReconnected(new WebSocketReconnectedEvent());

			verify(rtStockPriceStore).put(eq("005930"), any(RealtimeStockPrice.class));
			verify(rtStockPriceStore, never()).putIfAbsent(anyString(), any());
		}

		@Test
		@DisplayName("재시딩 중 일부 종목 실패해도 나머지 종목은 계속 처리된다 (2종목, sleep 2초)")
		void reseed_whenOneStockFails_othersAreProcessed() throws InterruptedException {
			Stock s1 = stock("005930");
			Stock s2 = stock("000660");
			when(stockRepository.findAll()).thenReturn(List.of(s1, s2));

			when(kisPriceClient.getCurrentPrice(TOKEN, "005930"))
				.thenThrow(new RuntimeException("REST 오류"));
			when(kisPriceClient.getCurrentPrice(TOKEN, "000660"))
				.thenReturn(mockPriceRes());

			sut.onWebSocketReconnected(new WebSocketReconnectedEvent());

			verify(rtStockPriceStore, never()).put(eq("005930"), any());
			verify(rtStockPriceStore).put(eq("000660"), any(RealtimeStockPrice.class));
		}

		@Test
		@DisplayName("이미 재시딩 진행 중이면 두 번째 이벤트는 skip된다 (REST 호출 없음)")
		void reseed_whenAlreadyInProgress_skipsSecondCall() throws Exception {
			setReseedInProgress(true);

			sut.onWebSocketReconnected(new WebSocketReconnectedEvent());

			verify(stockRepository, never()).findAll();
			verify(kisPriceClient, never()).getAccessToken();
			verify(kisPriceClient, never()).getCurrentPrice(anyString(), anyString());
			verify(rtStockPriceStore, never()).put(anyString(), any());
		}

		@Test
		@DisplayName("재시딩 정상 종료 후 reseedInProgress는 false로 복귀한다 (2호출, sleep 2초)")
		void reseed_resetsGuardFlagAfterCompletion() throws InterruptedException {
			Stock s = stock("005930");
			when(stockRepository.findAll()).thenReturn(List.of(s));
			when(kisPriceClient.getCurrentPrice(TOKEN, "005930")).thenReturn(mockPriceRes());

			sut.onWebSocketReconnected(new WebSocketReconnectedEvent());
			sut.onWebSocketReconnected(new WebSocketReconnectedEvent());

			// 두 번 다 정상 진입했으면 findAll이 2번 호출됨
			verify(stockRepository, times(2)).findAll();
		}
	}
}