package com.ux.relay.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket配置类
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理，并设置消息代理的前缀
        config.enableSimpleBroker("/topic", "/queue");
        
        // 设置应用程序的目标前缀
        config.setApplicationDestinationPrefixes("/app");
        
        // 设置用户目标前缀
        config.setUserDestinationPrefix("/user");
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册STOMP端点，支持SockJS
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*")  // Spring Boot 2.3版本使用setAllowedOrigins
                .withSockJS();

        // 注册告警端点
        registry.addEndpoint("/ws/alerts")
                .setAllowedOrigins("*")
                .withSockJS();

        // 注册实时监控端点
        registry.addEndpoint("/ws/metrics")
                .setAllowedOrigins("*")
                .withSockJS();
    }
}
