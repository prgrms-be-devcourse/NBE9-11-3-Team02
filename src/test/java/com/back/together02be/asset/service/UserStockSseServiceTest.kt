package com.back.together02be.asset.service

import com.back.together02be.stock.dto.RealtimeStockPrice
import com.back.together02be.stock.service.RealTimeStockPriceStore
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException

@ExtendWith(MockitoExtension::class)
class UserStockSseServiceTest {

    // 코틀린에서는 의존성 주입을 위해 지연 초기화(lateinit)를 사용합니다.
    @Mock
    lateinit var priceStore: RealTimeStockPriceStore

    @InjectMocks
    lateinit var userStockSseService: UserStockSseService

    @Test
    @DisplayName("성공: 구독된 Emitter들에게 실시간 시세를 브로드캐스팅한다")
    fun broadcastOwnedStocks_Success() { // throws Exception 생략
        // Given: 특정 종목(005930)에 대한 가짜 Emitter 등록
        val stockCode = "005930"
        // 코틀린에서 클래스 타입은 ::class.java 로 넘깁니다.
        val mockEmitter = mock(SseEmitter::class.java)
        userStockSseService.addEmitter(stockCode, mockEmitter)

        // Store에서 현재가 70,000원이 조회된다고 가정
        val mockPrice = RealtimeStockPrice.builder()
            .stockCode(stockCode)
            .price("70000")
            .build()

        // when은 코틀린의 예약어이므로 백틱(`)으로 감싸서 호출합니다.
        `when`(priceStore.get(stockCode)).thenReturn(mockPrice)

        // When: 스케줄러 브로드캐스트 로직 1회 수동 실행
        userStockSseService.broadcastOwnedStocks()

        // Then: 등록된 mockEmitter의 send() 메서드가 정확히 1번 호출되었는지 검증
        verify(mockEmitter, times(1)).send(any(SseEmitter.SseEventBuilder::class.java))
    }

    @Test
    @DisplayName("성공: 연결이 끊겨 전송에 실패할 경우 해당 Emitter를 목록에서 제거한다")
    fun broadcastOwnedStocks_Failure_RemovesEmitter() {
        // Given: Emitter 등록 및 현재가 세팅
        val stockCode = "005930"
        val mockEmitter = mock(SseEmitter::class.java)
        userStockSseService.addEmitter(stockCode, mockEmitter)

        val mockPrice = RealtimeStockPrice.builder()
            .stockCode(stockCode)
            .price("70000")
            .build()

        `when`(priceStore.get(stockCode)).thenReturn(mockPrice)

        // 핵심: 클라이언트와 연결이 끊겨 send() 시 IOException이 발생하도록 조작
        doThrow(IOException("Connection closed"))
            .`when`(mockEmitter).send(any(SseEmitter.SseEventBuilder::class.java))

        // When: 첫 번째 브로드캐스트 (이때 예외가 터지면서 catch 블록에서 removeEmitter 호출 예상)
        userStockSseService.broadcastOwnedStocks()

        // 두 번째 브로드캐스트 (제거되었다면 더 이상 send를 시도하지 않아야 함)
        userStockSseService.broadcastOwnedStocks()

        // Then: send() 시도는 최초 1번만 발생해야 함
        verify(mockEmitter, times(1)).send(any(SseEmitter.SseEventBuilder::class.java))
    }
}