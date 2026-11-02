package com.campus.lab_resource_platform.utilization.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final UtilizationWebSocketHandler utilizationWebSocketHandler;

    public WebSocketConfig(UtilizationWebSocketHandler utilizationWebSocketHandler) {
        this.utilizationWebSocketHandler = utilizationWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(utilizationWebSocketHandler, "/ws/utilization")
                .setAllowedOriginPatterns("*");
    }
}

