package com.back.together02be.support

import com.back.together02be.infra.kis.StockSubscriptionInitializer
import com.back.together02be.infra.kis.websocket.KisWebSocketClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
@ActiveProfiles("test")
abstract class IntegrationTestSupport {

    @MockitoBean
    protected lateinit var kisWebSocketClient: KisWebSocketClient

    @MockitoBean
    protected lateinit var stockSubscriptionInitializer: StockSubscriptionInitializer
}
