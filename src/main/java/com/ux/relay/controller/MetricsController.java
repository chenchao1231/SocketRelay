package com.ux.relay.controller;

import com.ux.relay.controller.ForwardRuleController.ApiResponse;
import com.ux.relay.service.MetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 监控指标控制器
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@RestController
@RequestMapping("/api/metrics")
@CrossOrigin(origins = "*")
public class MetricsController {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsController.class);
    
    @Autowired
    private MetricsService metricsService;
    
    /**
     * 获取当前指标快照
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentMetrics() {
        try {
            MetricsService.MetricsSnapshot metrics = metricsService.getCurrentMetrics();
            
            return ResponseEntity.ok(ApiResponse.success("当前指标获取成功", metrics));
            
        } catch (Exception e) {
            logger.error("获取当前指标异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取当前指标失败"));
        }
    }
    
    /**
     * 获取历史指标数据
     */
    @GetMapping("/history")
    public ResponseEntity<?> getHistoricalMetrics(@RequestParam(defaultValue = "24") int hours) {
        try {
            List<MetricsService.MetricsSnapshot> metrics = metricsService.getHistoricalMetrics(hours);
            
            return ResponseEntity.ok(ApiResponse.success("历史指标获取成功", metrics));
            
        } catch (Exception e) {
            logger.error("获取历史指标异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取历史指标失败"));
        }
    }
    
    /**
     * 获取最近24小时的指标数据
     */
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentMetrics() {
        try {
            List<MetricsService.MetricsSnapshot> metrics = metricsService.getRecentMetrics();
            
            return ResponseEntity.ok(ApiResponse.success("最近指标获取成功", metrics));
            
        } catch (Exception e) {
            logger.error("获取最近指标异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取最近指标失败"));
        }
    }
    
    /**
     * 获取系统性能信息
     */
    @GetMapping("/system-performance")
    public ResponseEntity<?> getSystemPerformance() {
        try {
            MetricsService.SystemPerformance performance = metricsService.getSystemPerformance();
            
            return ResponseEntity.ok(ApiResponse.success("系统性能信息获取成功", performance));
            
        } catch (Exception e) {
            logger.error("获取系统性能信息异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取系统性能信息失败"));
        }
    }
    
    /**
     * 获取监控仪表板数据
     */
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardData() {
        try {
            DashboardData dashboard = new DashboardData();
            
            // 当前指标
            MetricsService.MetricsSnapshot currentMetrics = metricsService.getCurrentMetrics();
            dashboard.setCurrentMetrics(currentMetrics);
            
            // 系统性能
            MetricsService.SystemPerformance systemPerformance = metricsService.getSystemPerformance();
            dashboard.setSystemPerformance(systemPerformance);
            
            // 最近1小时的指标趋势
            List<MetricsService.MetricsSnapshot> recentMetrics = metricsService.getHistoricalMetrics(1);
            dashboard.setRecentTrend(recentMetrics);
            
            return ResponseEntity.ok(ApiResponse.success("仪表板数据获取成功", dashboard));
            
        } catch (Exception e) {
            logger.error("获取仪表板数据异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取仪表板数据失败"));
        }
    }
    
    /**
     * 重置指标数据
     */
    @PostMapping("/reset")
    public ResponseEntity<?> resetMetrics() {
        try {
            metricsService.resetMetrics();
            
            return ResponseEntity.ok(ApiResponse.success("指标数据重置成功"));
            
        } catch (Exception e) {
            logger.error("重置指标数据异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("重置指标数据失败"));
        }
    }
    
    /**
     * 获取性能统计摘要
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getMetricsSummary() {
        try {
            MetricsSummary summary = new MetricsSummary();
            
            // 当前指标
            MetricsService.MetricsSnapshot current = metricsService.getCurrentMetrics();
            summary.setActiveConnections(current.getActiveConnections());
            summary.setTotalConnections(current.getTotalConnections());
            summary.setBytesPerSecond(current.getBytesPerSecond());
            summary.setErrorRate(current.getErrorRate());
            
            // 系统性能
            MetricsService.SystemPerformance performance = metricsService.getSystemPerformance();
            summary.setCpuUsage(performance.getCpuUsage());
            summary.setMemoryUsage(performance.getMemoryUsagePercent());
            
            // 计算平均值（最近1小时）
            List<MetricsService.MetricsSnapshot> recentMetrics = metricsService.getHistoricalMetrics(1);
            if (!recentMetrics.isEmpty()) {
                double avgBytesPerSecond = recentMetrics.stream()
                    .mapToLong(MetricsService.MetricsSnapshot::getBytesPerSecond)
                    .average().orElse(0.0);
                summary.setAvgBytesPerSecond((long) avgBytesPerSecond);
                
                double avgErrorRate = recentMetrics.stream()
                    .mapToDouble(MetricsService.MetricsSnapshot::getErrorRate)
                    .average().orElse(0.0);
                summary.setAvgErrorRate(avgErrorRate);
            }
            
            return ResponseEntity.ok(ApiResponse.success("指标摘要获取成功", summary));
            
        } catch (Exception e) {
            logger.error("获取指标摘要异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取指标摘要失败"));
        }
    }
    
    /**
     * 仪表板数据类
     */
    public static class DashboardData {
        private MetricsService.MetricsSnapshot currentMetrics;
        private MetricsService.SystemPerformance systemPerformance;
        private List<MetricsService.MetricsSnapshot> recentTrend;
        
        // Getters and Setters
        public MetricsService.MetricsSnapshot getCurrentMetrics() { return currentMetrics; }
        public void setCurrentMetrics(MetricsService.MetricsSnapshot currentMetrics) { this.currentMetrics = currentMetrics; }
        
        public MetricsService.SystemPerformance getSystemPerformance() { return systemPerformance; }
        public void setSystemPerformance(MetricsService.SystemPerformance systemPerformance) { this.systemPerformance = systemPerformance; }
        
        public List<MetricsService.MetricsSnapshot> getRecentTrend() { return recentTrend; }
        public void setRecentTrend(List<MetricsService.MetricsSnapshot> recentTrend) { this.recentTrend = recentTrend; }
    }
    
    /**
     * 指标摘要类
     */
    public static class MetricsSummary {
        private long activeConnections;
        private long totalConnections;
        private long bytesPerSecond;
        private long avgBytesPerSecond;
        private double errorRate;
        private double avgErrorRate;
        private double cpuUsage;
        private double memoryUsage;
        
        // Getters and Setters
        public long getActiveConnections() { return activeConnections; }
        public void setActiveConnections(long activeConnections) { this.activeConnections = activeConnections; }
        
        public long getTotalConnections() { return totalConnections; }
        public void setTotalConnections(long totalConnections) { this.totalConnections = totalConnections; }
        
        public long getBytesPerSecond() { return bytesPerSecond; }
        public void setBytesPerSecond(long bytesPerSecond) { this.bytesPerSecond = bytesPerSecond; }
        
        public long getAvgBytesPerSecond() { return avgBytesPerSecond; }
        public void setAvgBytesPerSecond(long avgBytesPerSecond) { this.avgBytesPerSecond = avgBytesPerSecond; }
        
        public double getErrorRate() { return errorRate; }
        public void setErrorRate(double errorRate) { this.errorRate = errorRate; }
        
        public double getAvgErrorRate() { return avgErrorRate; }
        public void setAvgErrorRate(double avgErrorRate) { this.avgErrorRate = avgErrorRate; }
        
        public double getCpuUsage() { return cpuUsage; }
        public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }
        
        public double getMemoryUsage() { return memoryUsage; }
        public void setMemoryUsage(double memoryUsage) { this.memoryUsage = memoryUsage; }
    }
}
