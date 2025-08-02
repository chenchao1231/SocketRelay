package com.ux.relay.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指标监控服务
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Service
public class MetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsService.class);
    
    @Value("${app.monitoring.metrics.retention-hours:24}")
    private int retentionHours;
    
    @Value("${app.monitoring.metrics.collection-interval:1000}")
    private long collectionInterval;
    
    // 实时指标
    private final AtomicLong activeConnections = new AtomicLong(0);
    private final AtomicLong totalConnections = new AtomicLong(0);
    private final AtomicLong connectionErrors = new AtomicLong(0);

    @Autowired(required = false)
    private com.ux.relay.controller.WebSocketController webSocketController;
    private final AtomicLong transferErrors = new AtomicLong(0);
    private final AtomicLong bytesTransferred = new AtomicLong(0);
    private final AtomicLong forwardingRuleCount = new AtomicLong(0);
    
    // 历史指标数据 (时间戳 -> 指标快照)
    private final Map<LocalDateTime, MetricsSnapshot> historicalMetrics = new ConcurrentHashMap<>();
    
    // 每秒统计
    private final AtomicLong lastSecondBytes = new AtomicLong(0);
    private final AtomicLong currentSecondBytes = new AtomicLong(0);
    private volatile LocalDateTime lastSecondTime = LocalDateTime.now();
    
    @PostConstruct
    public void init() {
        logger.info("指标监控服务初始化完成");
    }
    
    /**
     * 增加活跃连接数
     */
    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }
    
    /**
     * 减少活跃连接数
     */
    public void decrementActiveConnections() {
        activeConnections.decrementAndGet();
    }
    
    /**
     * 增加总连接数
     */
    public void incrementTotalConnections() {
        totalConnections.incrementAndGet();
    }
    
    /**
     * 增加连接错误数
     */
    public void incrementConnectionErrors() {
        connectionErrors.incrementAndGet();
    }
    
    /**
     * 增加传输错误数
     */
    public void incrementTransferErrors() {
        transferErrors.incrementAndGet();
    }
    
    /**
     * 增加传输字节数
     * 
     * @param bytes 字节数
     */
    public void addBytesTransferred(long bytes) {
        bytesTransferred.addAndGet(bytes);
        currentSecondBytes.addAndGet(bytes);
    }
    
    /**
     * 增加转发规则数
     */
    public void incrementForwardingRuleCount() {
        forwardingRuleCount.incrementAndGet();
    }
    
    /**
     * 减少转发规则数
     */
    public void decrementForwardingRuleCount() {
        forwardingRuleCount.decrementAndGet();
    }
    
    /**
     * 获取当前指标快照
     * 
     * @return 指标快照
     */
    public MetricsSnapshot getCurrentMetrics() {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.setTimestamp(LocalDateTime.now());
        snapshot.setActiveConnections(activeConnections.get());
        snapshot.setTotalConnections(totalConnections.get());
        snapshot.setConnectionErrors(connectionErrors.get());
        snapshot.setTransferErrors(transferErrors.get());
        snapshot.setBytesTransferred(bytesTransferred.get());
        snapshot.setForwardingRuleCount(forwardingRuleCount.get());
        snapshot.setBytesPerSecond(calculateBytesPerSecond());
        
        return snapshot;
    }
    
    /**
     * 获取历史指标数据
     * 
     * @param hours 小时数
     * @return 历史指标列表
     */
    public List<MetricsSnapshot> getHistoricalMetrics(int hours) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(hours);
        
        return historicalMetrics.entrySet().stream()
                .filter(entry -> entry.getKey().isAfter(cutoffTime))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    /**
     * 获取最近24小时的指标数据
     * 
     * @return 最近24小时的指标列表
     */
    public List<MetricsSnapshot> getRecentMetrics() {
        return getHistoricalMetrics(24);
    }
    
    /**
     * 计算每秒传输字节数
     * 
     * @return 每秒字节数
     */
    private long calculateBytesPerSecond() {
        LocalDateTime now = LocalDateTime.now();
        
        // 如果已经过了一秒，更新统计
        if (now.getSecond() != lastSecondTime.getSecond()) {
            lastSecondBytes.set(currentSecondBytes.getAndSet(0));
            lastSecondTime = now;
        }
        
        return lastSecondBytes.get();
    }
    
    /**
     * 定时收集指标数据
     */
    @Scheduled(fixedDelayString = "${app.monitoring.metrics.collection-interval:1000}")
    public void collectMetrics() {
        try {
            LocalDateTime now = LocalDateTime.now();
            MetricsSnapshot snapshot = getCurrentMetrics();
            
            // 保存指标快照
            historicalMetrics.put(now, snapshot);
            
            logger.debug("指标收集完成: 活跃连接={}, 总连接={}, 传输字节={}, 每秒字节={}", 
                        snapshot.getActiveConnections(), 
                        snapshot.getTotalConnections(),
                        snapshot.getBytesTransferred(),
                        snapshot.getBytesPerSecond());
            
        } catch (Exception e) {
            logger.error("指标收集异常", e);
        }
    }
    
    /**
     * 定时清理过期的历史指标数据
     */
    @Scheduled(fixedDelay = 300000) // 每5分钟执行一次
    public void cleanupHistoricalMetrics() {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(retentionHours);
            
            int removedCount = 0;
            Iterator<Map.Entry<LocalDateTime, MetricsSnapshot>> iterator = historicalMetrics.entrySet().iterator();
            
            while (iterator.hasNext()) {
                Map.Entry<LocalDateTime, MetricsSnapshot> entry = iterator.next();
                if (entry.getKey().isBefore(cutoffTime)) {
                    iterator.remove();
                    removedCount++;
                }
            }
            
            if (removedCount > 0) {
                logger.debug("清理过期指标数据: {} 条", removedCount);
            }
            
        } catch (Exception e) {
            logger.error("清理历史指标数据异常", e);
        }
    }
    
    /**
     * 获取系统性能统计
     * 
     * @return 性能统计
     */
    public SystemPerformance getSystemPerformance() {
        Runtime runtime = Runtime.getRuntime();
        
        SystemPerformance performance = new SystemPerformance();
        performance.setTotalMemory(runtime.totalMemory());
        performance.setFreeMemory(runtime.freeMemory());
        performance.setUsedMemory(runtime.totalMemory() - runtime.freeMemory());
        performance.setMaxMemory(runtime.maxMemory());
        performance.setAvailableProcessors(runtime.availableProcessors());
        performance.setCpuUsage(getCpuUsage());
        
        return performance;
    }
    
    /**
     * 获取CPU使用率（简单估算）
     * 
     * @return CPU使用率百分比
     */
    private double getCpuUsage() {
        // 这里使用简单的方法估算CPU使用率
        // 在生产环境中可以使用更精确的方法，如JMX
        long activeConn = activeConnections.get();
        long bytesPerSec = calculateBytesPerSecond();
        
        // 基于连接数和传输速率的简单估算
        double usage = Math.min(100.0, (activeConn * 0.1) + (bytesPerSec / 1024.0 / 1024.0 * 2));
        return Math.max(0.0, usage);
    }
    
    /**
     * 重置所有指标
     */
    public void resetMetrics() {
        logger.info("重置所有指标");
        
        activeConnections.set(0);
        totalConnections.set(0);
        connectionErrors.set(0);
        transferErrors.set(0);
        bytesTransferred.set(0);
        forwardingRuleCount.set(0);
        
        historicalMetrics.clear();
    }
    
    /**
     * 指标快照类
     */
    public static class MetricsSnapshot {
        private LocalDateTime timestamp;
        private long activeConnections;
        private long totalConnections;
        private long connectionErrors;
        private long transferErrors;
        private long bytesTransferred;
        private long forwardingRuleCount;
        private long bytesPerSecond;
        
        // Getters and Setters
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public long getActiveConnections() { return activeConnections; }
        public void setActiveConnections(long activeConnections) { this.activeConnections = activeConnections; }
        
        public long getTotalConnections() { return totalConnections; }
        public void setTotalConnections(long totalConnections) { this.totalConnections = totalConnections; }
        
        public long getConnectionErrors() { return connectionErrors; }
        public void setConnectionErrors(long connectionErrors) { this.connectionErrors = connectionErrors; }
        
        public long getTransferErrors() { return transferErrors; }
        public void setTransferErrors(long transferErrors) { this.transferErrors = transferErrors; }
        
        public long getBytesTransferred() { return bytesTransferred; }
        public void setBytesTransferred(long bytesTransferred) { this.bytesTransferred = bytesTransferred; }
        
        public long getForwardingRuleCount() { return forwardingRuleCount; }
        public void setForwardingRuleCount(long forwardingRuleCount) { this.forwardingRuleCount = forwardingRuleCount; }
        
        public long getBytesPerSecond() { return bytesPerSecond; }
        public void setBytesPerSecond(long bytesPerSecond) { this.bytesPerSecond = bytesPerSecond; }
        
        public double getErrorRate() {
            return totalConnections > 0 ? (double) connectionErrors / totalConnections : 0.0;
        }
    }
    
    /**
     * 系统性能类
     */
    public static class SystemPerformance {
        private long totalMemory;
        private long freeMemory;
        private long usedMemory;
        private long maxMemory;
        private int availableProcessors;
        private double cpuUsage;
        
        // Getters and Setters
        public long getTotalMemory() { return totalMemory; }
        public void setTotalMemory(long totalMemory) { this.totalMemory = totalMemory; }
        
        public long getFreeMemory() { return freeMemory; }
        public void setFreeMemory(long freeMemory) { this.freeMemory = freeMemory; }
        
        public long getUsedMemory() { return usedMemory; }
        public void setUsedMemory(long usedMemory) { this.usedMemory = usedMemory; }
        
        public long getMaxMemory() { return maxMemory; }
        public void setMaxMemory(long maxMemory) { this.maxMemory = maxMemory; }
        
        public int getAvailableProcessors() { return availableProcessors; }
        public void setAvailableProcessors(int availableProcessors) { this.availableProcessors = availableProcessors; }
        
        public double getCpuUsage() { return cpuUsage; }
        public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }
        
        public double getMemoryUsagePercent() {
            return maxMemory > 0 ? (double) usedMemory / maxMemory * 100 : 0.0;
        }
    }

    /**
     * 推送实时指标到WebSocket客户端
     */
    public void pushMetricsToWebSocket() {
        if (webSocketController != null) {
            try {
                MetricsSnapshot snapshot = getCurrentMetrics();
                Map<String, Object> metricsData = new HashMap<>();
                metricsData.put("activeConnections", snapshot.getActiveConnections());
                metricsData.put("totalConnections", snapshot.getTotalConnections());
                metricsData.put("connectionErrors", snapshot.getConnectionErrors());
                metricsData.put("transferErrors", snapshot.getTransferErrors());
                metricsData.put("bytesTransferred", snapshot.getBytesTransferred());
                metricsData.put("forwardingRuleCount", snapshot.getForwardingRuleCount());
                metricsData.put("bytesPerSecond", snapshot.getBytesPerSecond());
                metricsData.put("errorRate", snapshot.getErrorRate());

                webSocketController.sendMetrics(metricsData);
            } catch (Exception e) {
                logger.warn("推送WebSocket指标失败", e);
            }
        }
    }
}
