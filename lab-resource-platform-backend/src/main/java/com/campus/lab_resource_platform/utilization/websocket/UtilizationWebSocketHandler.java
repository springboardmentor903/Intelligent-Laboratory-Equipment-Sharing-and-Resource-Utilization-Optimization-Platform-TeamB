package com.campus.lab_resource_platform.utilization.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UtilizationWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(UtilizationWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket connected: session id={}. Total active clients={}", session.getId(), sessions.size());
        try {
            session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\",\"message\":\"Connected to LabResource Real-Time Utilization Stream\"}"));
        } catch (IOException e) {
            log.warn("Could not send welcome message to session {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket closed: session id={}. Remaining clients={}", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
        log.warn("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Echo / Ping-Pong handling
        String payload = message.getPayload();
        if ("PING".equalsIgnoreCase(payload)) {
            try {
                session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
            } catch (IOException ignored) {}
        }
    }

    /**
     * Broadcast live JSON event to all connected monitoring clients
     */
    public void broadcastEvent(String jsonPayload) {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(jsonPayload));
                } catch (IOException e) {
                    log.error("Failed to broadcast message to session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }
}

