package com.docusphere.backend.notification.config;

import com.docusphere.backend.authentication.service.JwtService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for notification system interceptors.
 * Creates beans for JWT authentication in WebSocket connections.
 */
@Configuration
public class NotificationInterceptorConfig {

    /**
     * Create JWT handshake interceptor bean
     * This interceptor validates JWT tokens during WebSocket handshake
     */
    @Bean
    @ConditionalOnBean(JwtService.class)
    public JwtHandshakeInterceptor jwtHandshakeInterceptor(JwtService jwtService) {
        return new JwtHandshakeInterceptor(jwtService);
    }

    /**
     * Create channel interceptor bean
     * This interceptor handles per-message authentication
     */
    @Bean
    public ChannelInterceptorAdapter channelInterceptorAdapter() {
        return new ChannelInterceptorAdapter();
    }
}
