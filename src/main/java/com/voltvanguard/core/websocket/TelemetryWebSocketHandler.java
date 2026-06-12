package com.voltvanguard.core.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.voltvanguard.core.kafka.event.VehicleTelemetryEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages all connected WebSocket clients and broadcasts live telemetry.
 *
 * <p>Thread safety: {@link CopyOnWriteArraySet} ensures that session
 * add/remove and iteration are safe under concurrent Kafka consumer threads.</p>
 *
 * <p>Registered at path {@code /ws/telemetry} via {@link WebSocketConfig}.
 * With the Spring Boot context-path {@code /api/v1} the full URL becomes:
 * {@code ws://localhost:8080/api/v1/ws/telemetry}</p>
 */
@Slf4j
@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

    /** Thread-safe set of all currently connected WebSocket sessions. */
    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    /**
     * Dedicated ObjectMapper for WebSocket payloads.
     * Serializes {@link java.time.Instant} as ISO-8601 strings (not epoch arrays).
     */
    private final ObjectMapper objectMapper;

    public TelemetryWebSocketHandler() {
        this.objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("WebSocket client connected: id={} remote={} total={}",
            session.getId(),
            session.getRemoteAddress(),
            sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("WebSocket client disconnected: id={} status={} remaining={}",
            session.getId(), status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error: id={} error={}",
            session.getId(), exception.getMessage());
        sessions.remove(session);
        try {
            if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) { /* best-effort */ }
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    /**
     * Serializes a {@link VehicleTelemetryEvent} into {@link TelemetryWebSocketMessage}
     * and broadcasts it to all connected clients.
     *
     * <p>Called from {@link com.voltvanguard.core.kafka.consumer.TelemetryProcessingService}
     * on every processed Kafka event — runs on the Kafka consumer thread pool.</p>
     *
     * @param event the telemetry event freshly processed from Kafka
     */
    public void broadcastTelemetry(VehicleTelemetryEvent event) {
        if (sessions.isEmpty()) return; // fast-path: no clients, skip serialization

        try {
            TelemetryWebSocketMessage message = TelemetryWebSocketMessage.from(event);
            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            int sent = 0;
            int failed = 0;
            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) {
                    sessions.remove(session);
                    continue;
                }
                try {
                    // synchronized on session: Spring WebSocket sessions are NOT thread-safe
                    synchronized (session) {
                        session.sendMessage(textMessage);
                    }
                    sent++;
                } catch (IOException e) {
                    failed++;
                    log.warn("Failed to send to client id={}: {}", session.getId(), e.getMessage());
                    sessions.remove(session);
                }
            }

            if (log.isTraceEnabled()) {
                log.trace("Broadcast telemetry: vehicle={} sent={} failed={}",
                    event.getVehicleId(), sent, failed);
            }

        } catch (Exception e) {
            log.error("WebSocket broadcast error for vehicle={}: {}",
                event.getVehicleId(), e.getMessage(), e);
        }
    }

    // ── Ping / handleTextMessage ──────────────────────────────────────────────

    /** Ignore any text messages from clients (read-only stream). */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Flutter clients don't send messages; ignore gracefully
        log.debug("Received text from client id={}: {}", session.getId(), message.getPayload());
    }

    /** Allow pongs from clients to keep the connection alive. */
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    public int getConnectedClientCount() {
        return sessions.size();
    }
}
