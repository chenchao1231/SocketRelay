package com.ux.relay.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket消息控制器
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-03
 */
@Controller
public class WebSocketController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    /**
     * 处理客户端发送的心跳消息
     */
    @MessageMapping("/heartbeat")
    @SendTo("/topic/heartbeat")
    public Map<String, Object> handleHeartbeat(Map<String, Object> message) {
        logger.debug("收到WebSocket心跳消息: {}", message);
        
        Map<String, Object> response = new HashMap<>();
        response.put("type", "heartbeat");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("status", "ok");
        
        return response;
    }
    
    /**
     * 处理客户端订阅告警消息
     */
    @MessageMapping("/subscribe/alerts")
    public void subscribeAlerts(Map<String, Object> message) {
        logger.info("客户端订阅告警消息: {}", message);
        
        // 发送欢迎消息
        Map<String, Object> welcomeMessage = new HashMap<>();
        welcomeMessage.put("type", "welcome");
        welcomeMessage.put("message", "已成功订阅告警消息");
        welcomeMessage.put("timestamp", LocalDateTime.now().toString());
        
        messagingTemplate.convertAndSend("/topic/alerts", welcomeMessage);
    }
    
    /**
     * 处理客户端订阅实时监控消息
     */
    @MessageMapping("/subscribe/metrics")
    public void subscribeMetrics(Map<String, Object> message) {
        logger.info("客户端订阅实时监控消息: {}", message);
        
        // 发送欢迎消息
        Map<String, Object> welcomeMessage = new HashMap<>();
        welcomeMessage.put("type", "welcome");
        welcomeMessage.put("message", "已成功订阅实时监控消息");
        welcomeMessage.put("timestamp", LocalDateTime.now().toString());
        
        messagingTemplate.convertAndSend("/topic/metrics", welcomeMessage);
    }
    
    /**
     * 发送告警消息到所有订阅的客户端
     */
    public void sendAlert(String alertType, String message, String level) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "alert");
        alert.put("alertType", alertType);
        alert.put("message", message);
        alert.put("level", level);
        alert.put("timestamp", LocalDateTime.now().toString());
        
        logger.info("发送告警消息: {}", alert);
        messagingTemplate.convertAndSend("/topic/alerts", alert);
    }
    
    /**
     * 发送实时监控数据到所有订阅的客户端
     */
    public void sendMetrics(Map<String, Object> metrics) {
        Map<String, Object> metricsMessage = new HashMap<>();
        metricsMessage.put("type", "metrics");
        metricsMessage.put("data", metrics);
        metricsMessage.put("timestamp", LocalDateTime.now().toString());
        
        logger.debug("发送实时监控数据: {}", metricsMessage);
        messagingTemplate.convertAndSend("/topic/metrics", metricsMessage);
    }
    
    /**
     * 发送系统状态更新消息
     */
    public void sendStatusUpdate(String component, String status, String message) {
        Map<String, Object> statusUpdate = new HashMap<>();
        statusUpdate.put("type", "status_update");
        statusUpdate.put("component", component);
        statusUpdate.put("status", status);
        statusUpdate.put("message", message);
        statusUpdate.put("timestamp", LocalDateTime.now().toString());
        
        logger.info("发送系统状态更新: {}", statusUpdate);
        messagingTemplate.convertAndSend("/topic/status", statusUpdate);
    }
}
