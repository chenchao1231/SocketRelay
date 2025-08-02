package com.ux.relay.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket事件监听器
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-03
 */
@Component
public class WebSocketEventListener {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);
    
    // 活跃连接计数器
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    
    // 会话信息存储
    private final ConcurrentHashMap<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    
    /**
     * WebSocket连接建立事件
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        // 记录会话信息
        SessionInfo sessionInfo = new SessionInfo(sessionId, System.currentTimeMillis());
        sessions.put(sessionId, sessionInfo);
        
        int currentConnections = activeConnections.incrementAndGet();
        
        logger.info("WebSocket连接建立: sessionId={}, 当前活跃连接数: {}", sessionId, currentConnections);
    }
    
    /**
     * WebSocket连接断开事件
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        // 移除会话信息
        SessionInfo sessionInfo = sessions.remove(sessionId);
        
        int currentConnections = activeConnections.decrementAndGet();
        
        if (sessionInfo != null) {
            long duration = System.currentTimeMillis() - sessionInfo.getConnectedAt();
            logger.info("WebSocket连接断开: sessionId={}, 连接持续时间: {}ms, 当前活跃连接数: {}", 
                       sessionId, duration, currentConnections);
        } else {
            logger.info("WebSocket连接断开: sessionId={}, 当前活跃连接数: {}", sessionId, currentConnections);
        }
    }
    
    /**
     * WebSocket订阅事件
     */
    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();
        
        // 更新会话订阅信息
        SessionInfo sessionInfo = sessions.get(sessionId);
        if (sessionInfo != null) {
            sessionInfo.addSubscription(destination);
        }
        
        logger.info("WebSocket订阅: sessionId={}, destination={}", sessionId, destination);
    }
    
    /**
     * WebSocket取消订阅事件
     */
    @EventListener
    public void handleWebSocketUnsubscribeListener(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String subscriptionId = headerAccessor.getSubscriptionId();
        
        // 更新会话订阅信息
        SessionInfo sessionInfo = sessions.get(sessionId);
        if (sessionInfo != null) {
            sessionInfo.removeSubscription(subscriptionId);
        }
        
        logger.info("WebSocket取消订阅: sessionId={}, subscriptionId={}", sessionId, subscriptionId);
    }
    
    /**
     * 获取当前活跃连接数
     */
    public int getActiveConnectionCount() {
        return activeConnections.get();
    }
    
    /**
     * 获取所有会话信息
     */
    public ConcurrentHashMap<String, SessionInfo> getSessions() {
        return new ConcurrentHashMap<>(sessions);
    }
    
    /**
     * 会话信息类
     */
    public static class SessionInfo {
        private final String sessionId;
        private final long connectedAt;
        private final ConcurrentHashMap<String, String> subscriptions = new ConcurrentHashMap<>();
        
        public SessionInfo(String sessionId, long connectedAt) {
            this.sessionId = sessionId;
            this.connectedAt = connectedAt;
        }
        
        public void addSubscription(String destination) {
            subscriptions.put(destination, destination);
        }
        
        public void removeSubscription(String subscriptionId) {
            subscriptions.remove(subscriptionId);
        }
        
        // Getters
        public String getSessionId() { return sessionId; }
        public long getConnectedAt() { return connectedAt; }
        public ConcurrentHashMap<String, String> getSubscriptions() { return new ConcurrentHashMap<>(subscriptions); }
    }
}
