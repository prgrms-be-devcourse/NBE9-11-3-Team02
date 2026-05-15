package com.back.together02be.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.back.together02be.infra.kis.StockSubscriptionInitializer;
import com.back.together02be.infra.kis.websocket.KisWebSocketClient;

@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

	@MockitoBean
	protected KisWebSocketClient kisWebSocketClient;

	@MockitoBean
	protected StockSubscriptionInitializer stockSubscriptionInitializer;
}
