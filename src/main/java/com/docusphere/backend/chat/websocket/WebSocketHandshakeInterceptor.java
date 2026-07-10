package com.docusphere.backend.chat.websocket;

import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.Cookie;

@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    static final String ACCESS_TOKEN_SESSION_KEY = "accessToken";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return true;
        }

        Cookie[] cookies = servletRequest.getServletRequest().getCookies();
        if (cookies == null) {
            return true;
        }

        for (Cookie cookie : cookies) {
            if ("accessToken".equalsIgnoreCase(cookie.getName())) {
                String value = cookie.getValue();
                if (value != null && !value.isBlank()) {
                    attributes.put(ACCESS_TOKEN_SESSION_KEY, value);
                }
                break;
            }
        }

        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }
}
