package com.ux.relay.service;

import com.ux.relay.service.MetricsService.MetricsSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 告警服务
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Service
public class AlertService {
    
    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private MetricsService metricsService;
    
    @Value("${app.monitoring.alerts.enabled:true}")
    private boolean alertsEnabled;
    
    @Value("${app.monitoring.alerts.error-rate-threshold:0.05}")
    private double errorRateThreshold;
    
    @Value("${app.monitoring.alerts.error-duration-threshold:60000}")
    private long errorDurationThreshold;
    
    @Value("${app.monitoring.alerts.notification-delay:10000}")
    private long notificationDelay;
    
    // 告警状态跟踪
    private final Map<String, AlertState> alertStates = new ConcurrentHashMap<>();
    private final AtomicLong alertIdGenerator = new AtomicLong(1);
    
    /**
     * 定时检查告警条件
     */
    @Scheduled(fixedDelay = 5000) // 每5秒检查一次
    public void checkAlerts() {
        if (!alertsEnabled) {
            return;
        }
        
        try {
            MetricsSnapshot currentMetrics = metricsService.getCurrentMetrics();
            
            // 检查错误率告警
            checkErrorRateAlert(currentMetrics);
            
            // 检查连接数告警
            checkConnectionAlert(currentMetrics);
            
            // 检查系统性能告警
            checkSystemPerformanceAlert();
            
        } catch (Exception e) {
            logger.error("检查告警条件异常", e);
        }
    }
    
    /**
     * 检查错误率告警
     */
    private void checkErrorRateAlert(MetricsSnapshot metrics) {
        String alertKey = "error_rate";
        double currentErrorRate = metrics.getErrorRate();
        
        if (currentErrorRate > errorRateThreshold) {
            AlertState state = alertStates.computeIfAbsent(alertKey, k -> new AlertState());
            
            if (state.firstOccurrence == null) {
                state.firstOccurrence = LocalDateTime.now();
            }
            
            long duration = java.time.Duration.between(state.firstOccurrence, LocalDateTime.now()).toMillis();
            
            if (duration >= errorDurationThreshold && !state.alerted) {
                sendAlert(Alert.builder()
                    .id(alertIdGenerator.getAndIncrement())
                    .type(AlertType.ERROR_RATE_HIGH)
                    .level(AlertLevel.WARNING)
                    .title("错误率过高")
                    .message(String.format("错误率已达到 %.2f%%，超过阈值 %.2f%%，持续时间 %d 秒", 
                            currentErrorRate * 100, errorRateThreshold * 100, duration / 1000))
                    .value(currentErrorRate)
                    .threshold(errorRateThreshold)
                    .timestamp(LocalDateTime.now())
                    .build());
                
                state.alerted = true;
                state.lastAlertTime = LocalDateTime.now();
            }
        } else {
            AlertState state = alertStates.get(alertKey);
            if (state != null && state.alerted) {
                // 错误率恢复正常
                sendAlert(Alert.builder()
                    .id(alertIdGenerator.getAndIncrement())
                    .type(AlertType.ERROR_RATE_RECOVERED)
                    .level(AlertLevel.INFO)
                    .title("错误率恢复正常")
                    .message(String.format("错误率已恢复到 %.2f%%", currentErrorRate * 100))
                    .value(currentErrorRate)
                    .threshold(errorRateThreshold)
                    .timestamp(LocalDateTime.now())
                    .build());
            }
            alertStates.remove(alertKey);
        }
    }
    
    /**
     * 检查连接数告警
     */
    private void checkConnectionAlert(MetricsSnapshot metrics) {
        String alertKey = "connection_count";
        long activeConnections = metrics.getActiveConnections();
        long maxConnections = 10000; // 从配置获取
        
        double connectionUsage = (double) activeConnections / maxConnections;
        
        if (connectionUsage > 0.8) { // 80%阈值
            AlertState state = alertStates.computeIfAbsent(alertKey, k -> new AlertState());
            
            if (!state.alerted || shouldSendPeriodicAlert(state)) {
                AlertLevel level = connectionUsage > 0.9 ? AlertLevel.CRITICAL : AlertLevel.WARNING;
                
                sendAlert(Alert.builder()
                    .id(alertIdGenerator.getAndIncrement())
                    .type(AlertType.CONNECTION_HIGH)
                    .level(level)
                    .title("连接数过高")
                    .message(String.format("当前活跃连接数 %d，使用率 %.1f%%", 
                            activeConnections, connectionUsage * 100))
                    .value(activeConnections)
                    .threshold(maxConnections * 0.8)
                    .timestamp(LocalDateTime.now())
                    .build());
                
                state.alerted = true;
                state.lastAlertTime = LocalDateTime.now();
            }
        } else {
            alertStates.remove(alertKey);
        }
    }
    
    /**
     * 检查系统性能告警
     */
    private void checkSystemPerformanceAlert() {
        MetricsService.SystemPerformance performance = metricsService.getSystemPerformance();
        
        // 检查内存使用率
        double memoryUsage = performance.getMemoryUsagePercent();
        if (memoryUsage > 80) {
            String alertKey = "memory_usage";
            AlertState state = alertStates.computeIfAbsent(alertKey, k -> new AlertState());
            
            if (!state.alerted || shouldSendPeriodicAlert(state)) {
                AlertLevel level = memoryUsage > 90 ? AlertLevel.CRITICAL : AlertLevel.WARNING;
                
                sendAlert(Alert.builder()
                    .id(alertIdGenerator.getAndIncrement())
                    .type(AlertType.MEMORY_HIGH)
                    .level(level)
                    .title("内存使用率过高")
                    .message(String.format("内存使用率 %.1f%%", memoryUsage))
                    .value(memoryUsage)
                    .threshold(80.0)
                    .timestamp(LocalDateTime.now())
                    .build());
                
                state.alerted = true;
                state.lastAlertTime = LocalDateTime.now();
            }
        } else {
            alertStates.remove("memory_usage");
        }
        
        // 检查CPU使用率
        double cpuUsage = performance.getCpuUsage();
        if (cpuUsage > 80) {
            String alertKey = "cpu_usage";
            AlertState state = alertStates.computeIfAbsent(alertKey, k -> new AlertState());
            
            if (!state.alerted || shouldSendPeriodicAlert(state)) {
                AlertLevel level = cpuUsage > 90 ? AlertLevel.CRITICAL : AlertLevel.WARNING;
                
                sendAlert(Alert.builder()
                    .id(alertIdGenerator.getAndIncrement())
                    .type(AlertType.CPU_HIGH)
                    .level(level)
                    .title("CPU使用率过高")
                    .message(String.format("CPU使用率 %.1f%%", cpuUsage))
                    .value(cpuUsage)
                    .threshold(80.0)
                    .timestamp(LocalDateTime.now())
                    .build());
                
                state.alerted = true;
                state.lastAlertTime = LocalDateTime.now();
            }
        } else {
            alertStates.remove("cpu_usage");
        }
    }
    
    /**
     * 发送告警
     */
    private void sendAlert(Alert alert) {
        try {
            logger.warn("发送告警: {} - {}", alert.getTitle(), alert.getMessage());
            
            // 通过WebSocket发送告警
            messagingTemplate.convertAndSend("/topic/alerts", alert);
            
        } catch (Exception e) {
            logger.error("发送告警异常", e);
        }
    }
    
    /**
     * 手动发送告警
     */
    public void sendManualAlert(String title, String message, AlertLevel level) {
        Alert alert = Alert.builder()
            .id(alertIdGenerator.getAndIncrement())
            .type(AlertType.MANUAL)
            .level(level)
            .title(title)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
        
        sendAlert(alert);
    }
    
    /**
     * 是否应该发送周期性告警
     */
    private boolean shouldSendPeriodicAlert(AlertState state) {
        if (state.lastAlertTime == null) {
            return true;
        }
        
        long timeSinceLastAlert = java.time.Duration.between(state.lastAlertTime, LocalDateTime.now()).toMillis();
        return timeSinceLastAlert >= notificationDelay;
    }
    
    /**
     * 告警状态类
     */
    private static class AlertState {
        LocalDateTime firstOccurrence;
        LocalDateTime lastAlertTime;
        boolean alerted = false;
    }
    
    /**
     * 告警类
     */
    public static class Alert {
        private long id;
        private AlertType type;
        private AlertLevel level;
        private String title;
        private String message;
        private double value;
        private double threshold;
        private LocalDateTime timestamp;
        
        // Builder pattern
        public static AlertBuilder builder() {
            return new AlertBuilder();
        }
        
        // Getters and Setters
        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        
        public AlertType getType() { return type; }
        public void setType(AlertType type) { this.type = type; }
        
        public AlertLevel getLevel() { return level; }
        public void setLevel(AlertLevel level) { this.level = level; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public double getValue() { return value; }
        public void setValue(double value) { this.value = value; }
        
        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public static class AlertBuilder {
            private Alert alert = new Alert();
            
            public AlertBuilder id(long id) { alert.id = id; return this; }
            public AlertBuilder type(AlertType type) { alert.type = type; return this; }
            public AlertBuilder level(AlertLevel level) { alert.level = level; return this; }
            public AlertBuilder title(String title) { alert.title = title; return this; }
            public AlertBuilder message(String message) { alert.message = message; return this; }
            public AlertBuilder value(double value) { alert.value = value; return this; }
            public AlertBuilder threshold(double threshold) { alert.threshold = threshold; return this; }
            public AlertBuilder timestamp(LocalDateTime timestamp) { alert.timestamp = timestamp; return this; }
            
            public Alert build() { return alert; }
        }
    }
    
    /**
     * 告警类型枚举
     */
    public enum AlertType {
        ERROR_RATE_HIGH,
        ERROR_RATE_RECOVERED,
        CONNECTION_HIGH,
        MEMORY_HIGH,
        CPU_HIGH,
        SYSTEM_ERROR,
        MANUAL
    }
    
    /**
     * 告警级别枚举
     */
    public enum AlertLevel {
        INFO,
        WARNING,
        CRITICAL
    }
}
