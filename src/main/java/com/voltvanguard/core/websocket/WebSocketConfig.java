package com.voltvanguard.core.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the raw WebSocket endpoint.
 *
 * <h3>Endpoint URL</h3>
 * <p>With {@code server.servlet.context-path: /api/v1} the full upgrade URL is:
 * <pre>ws://localhost:8080/api/v1/ws/telemetry</pre>
 * which matches exactly what the Flutter {@code WebSocketService} connects to.</p>
 *
 * <h3>Why raw WebSocket (not STOMP / SockJS)?</h3>
 * <p>Flutter's {@code web_socket_channel} package uses the native WebSocket
 * protocol. SockJS requires HTTP long-polling fallbacks that the Dart client
 * doesn't support, and STOMP adds a message-framing protocol overhead that
 * would require a Dart STOMP library. Raw WebSocket is simpler and fully
 * compatible with the existing Flutter implementation.</p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TelemetryWebSocketHandler telemetryHandler;

    public WebSocketConfig(TelemetryWebSocketHandler telemetryHandler) {
        this.telemetryHandler = telemetryHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            .addHandler(telemetryHandler, "/ws/telemetry")
            // Allow all origins so the Flutter mobile app (any IP) can connect.
            // In production, restrict to the actual app origin or use a whitelist.
            .setAllowedOrigins("*");
    }
}
