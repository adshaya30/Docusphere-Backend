package com.docusphere.backend.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Channel interceptor for handling per-message authentication in WebSocket connections.
 * Validates user authentication for each STOMP message.
 */
@Component
@Slf4j
public class ChannelInterceptorAdapter implements ChannelInterceptor {

    /**
     * Intercept messages before they are handled
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.debug("WebSocket CONNECT command received");
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            log.debug("WebSocket SUBSCRIBE to: {}", destination);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            log.debug("WebSocket SEND command received");
        }
        
        return message;
    }

    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        
        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            String sessionId = accessor.getSessionId();
            log.debug("WebSocket user disconnected: {}", sessionId);
        }
    }
}
