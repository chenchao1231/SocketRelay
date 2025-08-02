package com.ux.relay.controller;

import com.ux.relay.controller.ForwardRuleController.ApiResponse;
import com.ux.relay.entity.ConnectionInfo;
import com.ux.relay.entity.ForwardRule;
import com.ux.relay.service.ConnectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 连接管理控制器
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@RestController
@RequestMapping("/api/connections")
@CrossOrigin(origins = "*")
public class ConnectionController {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionController.class);
    
    @Autowired
    private ConnectionService connectionService;
    
    /**
     * 获取活跃连接列表
     */
    @GetMapping("/active")
    public ResponseEntity<?> getActiveConnections(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(defaultValue = "connectedAt") String sort,
                                                 @RequestParam(defaultValue = "desc") String direction) {
        try {
            Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? 
                                         Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
            
            Page<ConnectionInfo> connections = connectionService.findActiveConnections(pageable);
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", connections));
            
        } catch (Exception e) {
            logger.error("获取活跃连接异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取活跃连接失败"));
        }
    }
    
    /**
     * 获取所有活跃连接（不分页）
     */
    @GetMapping("/active/all")
    public ResponseEntity<?> getAllActiveConnections() {
        try {
            List<ConnectionInfo> connections = connectionService.findActiveConnections();
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", connections));
            
        } catch (Exception e) {
            logger.error("获取所有活跃连接异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取活跃连接失败"));
        }
    }
    
    /**
     * 根据规则ID获取连接
     */
    @GetMapping("/by-rule/{ruleId}")
    public ResponseEntity<?> getConnectionsByRule(@PathVariable Long ruleId) {
        try {
            List<ConnectionInfo> connections = connectionService.findByRuleId(ruleId);
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", connections));
            
        } catch (Exception e) {
            logger.error("根据规则ID获取连接异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取连接失败"));
        }
    }
    
    /**
     * 根据协议类型获取活跃连接
     */
    @GetMapping("/by-protocol/{protocol}")
    public ResponseEntity<?> getConnectionsByProtocol(@PathVariable ForwardRule.ProtocolType protocol) {
        try {
            List<ConnectionInfo> connections = connectionService.findActiveConnectionsByProtocol(protocol);
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", connections));
            
        } catch (Exception e) {
            logger.error("根据协议类型获取连接异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取连接失败"));
        }
    }
    
    /**
     * 获取连接统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getConnectionStatistics() {
        try {
            ConnectionService.ConnectionStatistics statistics = connectionService.getConnectionStatistics();
            
            return ResponseEntity.ok(ApiResponse.success("统计信息获取成功", statistics));
            
        } catch (Exception e) {
            logger.error("获取连接统计信息异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取统计信息失败"));
        }
    }
    
    /**
     * 获取流量统计信息
     */
    @GetMapping("/traffic-statistics")
    public ResponseEntity<?> getTrafficStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            ConnectionService.TrafficStatistics statistics = 
                connectionService.getTrafficStatistics(startTime, endTime);
            
            return ResponseEntity.ok(ApiResponse.success("流量统计获取成功", statistics));
            
        } catch (Exception e) {
            logger.error("获取流量统计信息异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取流量统计失败"));
        }
    }
    
    /**
     * 获取指定时间范围内的连接
     */
    @GetMapping("/by-time-range")
    public ResponseEntity<?> getConnectionsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        try {
            List<ConnectionInfo> connections = connectionService.findConnectionsBetween(startTime, endTime);
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", connections));
            
        } catch (Exception e) {
            logger.error("根据时间范围获取连接异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取连接失败"));
        }
    }
    
    /**
     * 获取最近的连接记录
     */
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentConnections(@RequestParam(defaultValue = "50") int limit) {
        try {
            List<ConnectionInfo> connections = connectionService.findRecentConnections(limit);
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", connections));
            
        } catch (Exception e) {
            logger.error("获取最近连接记录异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取连接记录失败"));
        }
    }
    
    /**
     * 获取连接详情
     */
    @GetMapping("/{connectionId}")
    public ResponseEntity<?> getConnectionDetail(@PathVariable String connectionId) {
        try {
            Optional<ConnectionInfo> connection = connectionService.findByConnectionId(connectionId);
            
            if (connection.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success("查询成功", connection.get()));
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("获取连接详情异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取连接详情失败"));
        }
    }
    
    /**
     * 获取实时连接概览
     */
    @GetMapping("/overview")
    public ResponseEntity<?> getConnectionOverview() {
        try {
            ConnectionOverview overview = new ConnectionOverview();
            
            // 获取连接统计
            ConnectionService.ConnectionStatistics stats = connectionService.getConnectionStatistics();
            overview.setActiveConnections(stats.getActiveConnections());
            overview.setTcpConnections(stats.getTcpConnections());
            overview.setUdpConnections(stats.getUdpConnections());
            
            // 获取最近1小时的流量统计
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusHours(1);
            ConnectionService.TrafficStatistics trafficStats = 
                connectionService.getTrafficStatistics(startTime, endTime);
            overview.setHourlyBytesReceived(trafficStats.getBytesReceived());
            overview.setHourlyBytesSent(trafficStats.getBytesSent());
            overview.setHourlyPacketsReceived(trafficStats.getPacketsReceived());
            overview.setHourlyPacketsSent(trafficStats.getPacketsSent());
            
            return ResponseEntity.ok(ApiResponse.success("连接概览获取成功", overview));
            
        } catch (Exception e) {
            logger.error("获取连接概览异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取连接概览失败"));
        }
    }
    
    /**
     * 连接概览数据类
     */
    public static class ConnectionOverview {
        private long activeConnections;
        private long tcpConnections;
        private long udpConnections;
        private long hourlyBytesReceived;
        private long hourlyBytesSent;
        private long hourlyPacketsReceived;
        private long hourlyPacketsSent;
        
        // Getters and Setters
        public long getActiveConnections() { return activeConnections; }
        public void setActiveConnections(long activeConnections) { this.activeConnections = activeConnections; }
        
        public long getTcpConnections() { return tcpConnections; }
        public void setTcpConnections(long tcpConnections) { this.tcpConnections = tcpConnections; }
        
        public long getUdpConnections() { return udpConnections; }
        public void setUdpConnections(long udpConnections) { this.udpConnections = udpConnections; }
        
        public long getHourlyBytesReceived() { return hourlyBytesReceived; }
        public void setHourlyBytesReceived(long hourlyBytesReceived) { this.hourlyBytesReceived = hourlyBytesReceived; }
        
        public long getHourlyBytesSent() { return hourlyBytesSent; }
        public void setHourlyBytesSent(long hourlyBytesSent) { this.hourlyBytesSent = hourlyBytesSent; }
        
        public long getHourlyPacketsReceived() { return hourlyPacketsReceived; }
        public void setHourlyPacketsReceived(long hourlyPacketsReceived) { this.hourlyPacketsReceived = hourlyPacketsReceived; }
        
        public long getHourlyPacketsSent() { return hourlyPacketsSent; }
        public void setHourlyPacketsSent(long hourlyPacketsSent) { this.hourlyPacketsSent = hourlyPacketsSent; }
        
        public long getTotalHourlyBytes() { return hourlyBytesReceived + hourlyBytesSent; }
        public long getTotalHourlyPackets() { return hourlyPacketsReceived + hourlyPacketsSent; }
    }
}
