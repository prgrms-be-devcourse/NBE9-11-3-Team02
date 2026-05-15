package com.back.together02be.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.back.together02be.infra.kis.StockSubscriptionInitializer;
import com.back.together02be.infra.kis.websocket.KisWebSocketClient;

@TestConfiguration
public class TestMockConfig {

    @Bean(name = "kisWebSocketClient")
    public KisWebSocketClient kisWebSocketClient() {
        return Mockito.mock(KisWebSocketClient.class);
    }

    @Bean(name = "stockSubscriptionInitializer")
    public StockSubscriptionInitializer stockSubscriptionInitializer() {
        return Mockito.mock(StockSubscriptionInitializer.class);
    }
}