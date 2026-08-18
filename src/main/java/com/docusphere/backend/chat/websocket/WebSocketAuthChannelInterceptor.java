package com.docusphere.backend.chat.websocket;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import com.docusphere.backend.authentication.service.JwtService;

@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public WebSocketAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = org.springframework.messaging.support.MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            Long userId = jwtService.extractUserId(token);
            Principal principal = () -> com.docusphere.backend.notification.util.NotificationUserIds.fromUserId(userId).toString();
            accessor.setUser(principal);
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String raw = authHeaders.get(0);
            if (raw != null && raw.startsWith("Bearer ")) {
                String token = raw.substring(7);
                if (!token.isBlank()) {
                    return token;
                }
            }
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object cookieToken = sessionAttributes.get(WebSocketHandshakeInterceptor.ACCESS_TOKEN_SESSION_KEY);
            if (cookieToken != null && !cookieToken.toString().isBlank()) {
                return cookieToken.toString();
            }
        }

        throw new IllegalArgumentException("Missing Authorization header or accessToken cookie in websocket CONNECT");
    }
}
