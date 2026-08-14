package com.docusphere.backend.notification.controller;

import com.docusphere.backend.notification.dto.WebSocketNotificationDTO;
import com.docusphere.backend.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket controller for handling real-time notification messages.
 * Handles STOMP protocol messages for notifications.
 */
@Controller
@Slf4j
public class NotificationWebSocketController {

    private final NotificationService notificationService;

    public NotificationWebSocketController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Handle incoming STOMP messages at /app/notifications/ping
     * Used for testing WebSocket connection
     */
    @MessageMapping("/notifications/ping")
    public void handlePing(SimpMessageHeaderAccessor headerAccessor, Principal principal) {
        if (principal != null) {
            log.debug("Ping received from user: {}", principal.getName());
        }
    }

    /**
     * Handle subscribe message
     * Sends unread notifications count when user connects
     */
    @MessageMapping("/notifications/subscribe")
    public void handleSubscribe(SimpMessageHeaderAccessor headerAccessor, Principal principal) {
        if (principal != null) {
            log.info("User subscribed to notifications: {}", principal.getName());
        }
    }

    /**
     * Handle unsubscribe message
     */
    @MessageMapping("/notifications/unsubscribe")
    public void handleUnsubscribe(SimpMessageHeaderAccessor headerAccessor, Principal principal) {
        if (principal != null) {
            log.info("User unsubscribed from notifications: {}", principal.getName());
        }
    }

    /**
     * Handle custom notification test message
     */
    @MessageMapping("/notifications/test")
    public void handleTestNotification(@Payload WebSocketNotificationDTO message, Principal principal) {
        if (principal != null) {
            log.debug("Test notification received from user: {}", principal.getName());
        }
    }
}
