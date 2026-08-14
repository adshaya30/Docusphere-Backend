package com.docusphere.backend.notification.config;

import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.notification.util.NotificationUserIds;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket handshake interceptor for JWT authentication.
 * Extracts JWT token from WebSocket connection and validates it.
 * Sets the authenticated user as Principal for use in STOMP handlers.
 */
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Validate JWT token before WebSocket handshake
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            // Extract JWT token from query parameter
            String query = request.getURI().getQuery();
            String token = null;

            if (query != null) {
                for (String param : query.split("&")) {
                    if (param.startsWith("token=")) {
                        token = param.substring(6);
                        break;
                    }
                }
            }

            // If no token in query, try Authorization header
            if (token == null) {
                String authHeader = request.getHeaders().getFirst("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            // Validate token and extract user info
            if (token != null && !token.isEmpty()) {
                try {
                    Long userId = jwtService.extractUserId(token);
                    String username = jwtService.extractUsername(token);
                    
                    if (userId != null && username != null) {
                        // Principal must match NotificationService.convertAndSendToUser (derived UUID)
                        String notificationUserKey = NotificationUserIds.fromUserId(userId).toString();
                        attributes.put("userId", notificationUserKey);
                        attributes.put("username", username);

                        attributes.put("simpUser", new StompPrincipal(notificationUserKey));
                        
                        log.debug("WebSocket user authenticated: {} ({})", username, userId);
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("Token validation failed: {}", e.getMessage());
                }
            }

            log.warn("WebSocket connection attempt without valid token - allowing anyway for compatibility");
            return true; // Allow connection; user won't receive user-specific messages without auth

        } catch (Exception e) {
            log.error("Error validating WebSocket token - allowing connection anyway", e);
            return true;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket handshake error", exception);
        }
    }

    /**
     * Simple Principal implementation for STOMP
     */
    private static class StompPrincipal implements Principal {
        private final String name;

        StompPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
