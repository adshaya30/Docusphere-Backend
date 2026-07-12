package com.docusphere.backend.notification.support;

import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.notification.util.NotificationUserIds;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotificationAuthSupport {

    private final JwtService jwtService;

    public NotificationAuthSupport(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public UUID extractUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                if ("accessToken".equalsIgnoreCase(c.getName()) || "jwt".equalsIgnoreCase(c.getName()) || "Authorization".equalsIgnoreCase(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Invalid or missing token");
        }
        Long userId = jwtService.extractUserId(token);
        return NotificationUserIds.fromUserId(userId);
    }

    public Long getRawUserId(String token) {
        return jwtService.extractUserId(token);
    }
}
