package com.back.together02be.asset.service;

import com.back.together02be.stock.dto.RealtimeStockPrice;
import com.back.together02be.stock.service.RealTimeStockPriceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserStockSseServiceTest {

    @Mock
    RealTimeStockPriceStore priceStore;

    @InjectMocks
    UserStockSseService userStockSseService;

    private SseEmitter emitter;
    private final String stockCode = "005930"; // 테스트용 종목 코드

    @BeforeEach
    void setUp() {
        // 매 테스트마다 Emitter를 생성하고 Map에 추가합니다.
        emitter = userStockSseService.createEmitter();
        userStockSseService.addEmitter(stockCode, emitter);
    }

    @Test
    @DisplayName("성공: 구독된 Emitter들에게 실시간 시세를 브로드캐스팅한다")
    void broadcastOwnedStocks_Success() throws Exception {
        // Given: 특정 종목(005930)에 대한 가짜 Emitter 등록
        String stockCode = "005930";
        SseEmitter mockEmitter = mock(SseEmitter.class);
        userStockSseService.addEmitter(stockCode, mockEmitter);

        // Store에서 현재가 70,000원이 조회된다고 가정
        RealtimeStockPrice mockPrice = RealtimeStockPrice.builder()
                .stockCode(stockCode)
                .price("70000")
                .build();
        when(priceStore.get(stockCode)).thenReturn(mockPrice);

        // When: 스케줄러 브로드캐스트 로직 1회 수동 실행
        userStockSseService.broadcastOwnedStocks();

        // Then: 등록된 mockEmitter의 send() 메서드가 정확히 1번 호출되었는지 검증
        verify(mockEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("성공: 연결이 끊겨 전송에 실패할 경우 해당 Emitter를 목록에서 제거한다")
    void broadcastOwnedStocks_Failure_RemovesEmitter() throws Exception {
        // Given: Emitter 등록 및 현재가 세팅
        String stockCode = "005930";
        SseEmitter mockEmitter = mock(SseEmitter.class);
        userStockSseService.addEmitter(stockCode, mockEmitter);

        RealtimeStockPrice mockPrice = RealtimeStockPrice.builder()
                .stockCode(stockCode)
                .price("70000")
                .build();
        when(priceStore.get(stockCode)).thenReturn(mockPrice);

        // 핵심: 클라이언트와 연결이 끊겨 send() 시 IOException이 발생하도록 조작
        doThrow(new IOException("Connection closed")).when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));

        // When: 첫 번째 브로드캐스트 (이때 예외가 터지면서 catch 블록에서 removeEmitter 호출 예상)
        userStockSseService.broadcastOwnedStocks();

        // 두 번째 브로드캐스트 (제거되었다면 더 이상 send를 시도하지 않아야 함)
        userStockSseService.broadcastOwnedStocks();

        // Then: send() 시도는 최초 1번만 발생해야 함
        verify(mockEmitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("SseEmitter가 정상적으로 10분 타임아웃과 함께 생성되고 맵에 추가된다")
    void addEmitter_Success() throws Exception {
        Map<String, List<SseEmitter>> emittersMap = getEmittersMap();

        assertThat(emittersMap.get(stockCode)).hasSize(1);
        assertThat(emittersMap.get(stockCode).get(0)).isEqualTo(emitter);
        assertThat(emitter.getTimeout()).isEqualTo(600000L); // 10분 = 600,000ms
    }

    @Test
    @DisplayName("클라이언트 브라우저 종료 등(onCompletion) 발생 시 Emitter가 맵에서 일괄 제거된다")
    void removeEmitter_OnCompletion() throws Exception {
        // given: Emitter에 등록된 completion 콜백 추출
        Runnable completionCallback = getRunnableCallback(emitter, "completionCallback");

        // when: 클라이언트의 연결 종료를 강제 시뮬레이션 (콜백 실행)
        completionCallback.run();

        // then: 해당 종목의 구독 리스트에서 Emitter가 완전히 삭제되었는지 검증
        Map<String, List<SseEmitter>> emittersMap = getEmittersMap();
        assertThat(emittersMap.get(stockCode)).isEmpty();
    }

    @Test
    @DisplayName("서버 설정 타임아웃(onTimeout) 발생 시 Emitter가 맵에서 일괄 제거된다")
    void removeEmitter_OnTimeout() throws Exception {
        // given: Emitter에 등록된 timeout 콜백 추출
        Runnable timeoutCallback = getRunnableCallback(emitter, "timeoutCallback");

        // when: 타임아웃 강제 시뮬레이션
        timeoutCallback.run();

        // then
        Map<String, List<SseEmitter>> emittersMap = getEmittersMap();
        assertThat(emittersMap.get(stockCode)).isEmpty();
    }

    @Test
    @DisplayName("네트워크 에러(onError) 발생 시 Emitter가 맵에서 일괄 제거된다")
    void removeEmitter_OnError() throws Exception {
        // given: Emitter에 등록된 error 콜백 추출
        Consumer<Throwable> errorCallback = getErrorCallback(emitter);

        // when: 네트워크 예외 강제 발생 시뮬레이션
        errorCallback.accept(new RuntimeException("Network Error Simulation"));

        // then
        Map<String, List<SseEmitter>> emittersMap = getEmittersMap();
        assertThat(emittersMap.get(stockCode)).isEmpty();
    }

    @Test
    @DisplayName("스케줄러에 의한 데이터 브로드캐스팅 중 예외가 발생하면 해당 Emitter가 제거된다")
    void removeEmitter_OnSendException() throws Exception {
        // given
        SseEmitter spyEmitter = spy(emitter);
        getEmittersMap().get(stockCode).set(0, spyEmitter); // 테스트용 Spy 객체로 바꿔치기

        // 시세가 존재한다고 가정
        RealtimeStockPrice mockPrice = RealtimeStockPrice.builder()
                .price(String.valueOf(50000))
                .build();

        when(priceStore.get(stockCode)).thenReturn(mockPrice);
        // send 호출 시 강제로 예외 발생
        doThrow(new IOException("전송 실패")).when(spyEmitter).send(any(SseEmitter.SseEventBuilder.class));

        // when: 스케줄러 브로드캐스트 메서드 수동 실행
        userStockSseService.broadcastOwnedStocks();

        // then: 에러가 캐치되어 맵에서 제거되었는지 확인
        assertThat(getEmittersMap().get(stockCode)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<SseEmitter>> getEmittersMap() throws Exception {
        Field field = UserStockSseService.class.getDeclaredField("emittersMap");
        field.setAccessible(true);
        return (Map<String, List<SseEmitter>>) field.get(userStockSseService);
    }

    private Runnable getRunnableCallback(SseEmitter emitter, String fieldName) throws Exception {
        Field field = ResponseBodyEmitter.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Runnable) field.get(emitter);
    }

    @SuppressWarnings("unchecked")
    private Consumer<Throwable> getErrorCallback(SseEmitter emitter) throws Exception {
        Field field = ResponseBodyEmitter.class.getDeclaredField("errorCallback");
        field.setAccessible(true);
        return (Consumer<Throwable>) field.get(emitter);
    }
}