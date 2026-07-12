package com.docusphere.backend.chat.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.beans.factory.ObjectProvider;

import com.docusphere.backend.Common.config.AppConfig;
import com.docusphere.backend.notification.config.ChannelInterceptorAdapter;
import com.docusphere.backend.notification.config.JwtHandshakeInterceptor;

@Configuration("webSocketConfig")
@EnableWebSocketMessageBroker
@EnableScheduling
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppConfig appConfig;
    private final WebSocketAuthChannelInterceptor authChannelInterceptor;
    private final WebSocketHandshakeInterceptor handshakeInterceptor;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final ChannelInterceptorAdapter notificationChannelInterceptor;

    public WebSocketConfig(
            AppConfig appConfig,
            WebSocketAuthChannelInterceptor authChannelInterceptor,
            WebSocketHandshakeInterceptor handshakeInterceptor,
            ObjectProvider<JwtHandshakeInterceptor> jwtHandshakeInterceptorProvider,
            ObjectProvider<ChannelInterceptorAdapter> notificationChannelInterceptorProvider) {
        this.appConfig = appConfig;
        this.authChannelInterceptor = authChannelInterceptor;
        this.handshakeInterceptor = handshakeInterceptor;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptorProvider.getIfAvailable();
        this.notificationChannelInterceptor = notificationChannelInterceptorProvider.getIfAvailable();
    }

    @Bean
    public TaskScheduler webSocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue")
              .setHeartbeatValue(new long[]{10000, 10000})
              .setTaskScheduler(webSocketTaskScheduler());
              
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Chat endpoint
        registry.addEndpoint("/ws-chat")
            .setAllowedOriginPatterns(appConfig.getFrontendUrl())
            .addInterceptors(handshakeInterceptor)
            .withSockJS();

        // Notification endpoints with SockJS
        var notificationEndpoint = registry
            .addEndpoint("/ws/notifications")
            .setAllowedOriginPatterns("*");
            
        if (jwtHandshakeInterceptor != null) {
            notificationEndpoint.addInterceptors(jwtHandshakeInterceptor);
        }
        notificationEndpoint.withSockJS();

        // Native WebSocket for notifications
        var nativeNotificationEndpoint = registry
            .addEndpoint("/ws/notifications")
            .setAllowedOriginPatterns("*");
            
        if (jwtHandshakeInterceptor != null) {
            nativeNotificationEndpoint.addInterceptors(jwtHandshakeInterceptor);
        }
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
        if (notificationChannelInterceptor != null) {
            registration.interceptors(notificationChannelInterceptor);
        }
    }
}
