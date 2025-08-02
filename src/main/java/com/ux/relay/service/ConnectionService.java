package com.ux.relay.service;

import com.ux.relay.entity.ConnectionInfo;
import com.ux.relay.entity.ForwardRule;
import com.ux.relay.repository.ConnectionInfoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 连接服务
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Service
@Transactional
public class ConnectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionService.class);
    
    @Autowired
    private ConnectionInfoRepository connectionInfoRepository;
    
    @Value("${app.forwarding.connection.idle-timeout:300000}")
    private long idleTimeout;
    
    @Value("${app.audit.retention-days:30}")
    private int retentionDays;
    
    /**
     * 保存连接信息
     * 
     * @param connectionInfo 连接信息
     * @return 保存的连接信息
     */
    public ConnectionInfo saveConnection(ConnectionInfo connectionInfo) {
        logger.debug("保存连接信息: {}", connectionInfo.getConnectionId());
        return connectionInfoRepository.save(connectionInfo);
    }
    
    /**
     * 更新连接信息
     * 
     * @param connectionInfo 连接信息
     * @return 更新的连接信息
     */
    public ConnectionInfo updateConnection(ConnectionInfo connectionInfo) {
        logger.debug("更新连接信息: {}", connectionInfo.getConnectionId());
        return connectionInfoRepository.save(connectionInfo);
    }
    
    /**
     * 根据连接ID查询连接信息
     * 
     * @param connectionId 连接ID
     * @return 连接信息
     */
    @Transactional(readOnly = true)
    public Optional<ConnectionInfo> findByConnectionId(String connectionId) {
        return connectionInfoRepository.findByConnectionId(connectionId);
    }
    
    /**
     * 查询活跃连接
     * 
     * @return 活跃连接列表
     */
    @Transactional(readOnly = true)
    public List<ConnectionInfo> findActiveConnections() {
        return connectionInfoRepository.findActiveConnections();
    }
    
    /**
     * 分页查询活跃连接
     * 
     * @param pageable 分页参数
     * @return 活跃连接分页结果
     */
    @Transactional(readOnly = true)
    public Page<ConnectionInfo> findActiveConnections(Pageable pageable) {
        return connectionInfoRepository.findAll(pageable);
    }
    
    /**
     * 根据规则ID查询连接
     * 
     * @param ruleId 规则ID
     * @return 连接列表
     */
    @Transactional(readOnly = true)
    public List<ConnectionInfo> findByRuleId(Long ruleId) {
        return connectionInfoRepository.findByRuleId(ruleId);
    }
    
    /**
     * 根据协议类型查询活跃连接
     * 
     * @param protocol 协议类型
     * @return 活跃连接列表
     */
    @Transactional(readOnly = true)
    public List<ConnectionInfo> findActiveConnectionsByProtocol(ForwardRule.ProtocolType protocol) {
        return connectionInfoRepository.findActiveConnectionsByProtocol(protocol);
    }
    
    /**
     * 统计活跃连接数量
     * 
     * @return 活跃连接数量
     */
    @Transactional(readOnly = true)
    public long countActiveConnections() {
        return connectionInfoRepository.countActiveConnections();
    }
    
    /**
     * 根据协议统计活跃连接数量
     * 
     * @return 协议连接数统计
     */
    @Transactional(readOnly = true)
    public List<Object[]> countActiveConnectionsByProtocol() {
        return connectionInfoRepository.countActiveConnectionsByProtocol();
    }
    
    /**
     * 更新连接流量统计
     * 
     * @param connectionId 连接ID
     * @param bytesReceived 接收字节数
     * @param bytesSent 发送字节数
     * @param packetsReceived 接收包数
     * @param packetsSent 发送包数
     */
    @Async
    public void updateTrafficStats(String connectionId, Long bytesReceived, Long bytesSent, 
                                  Long packetsReceived, Long packetsSent) {
        try {
            int updated = connectionInfoRepository.updateTrafficStats(
                connectionId, bytesReceived, bytesSent, packetsReceived, packetsSent);
            
            if (updated == 0) {
                logger.warn("更新连接流量统计失败，连接不存在: {}", connectionId);
            }
            
        } catch (Exception e) {
            logger.error("更新连接流量统计异常: {}", connectionId, e);
        }
    }
    
    /**
     * 更新连接状态
     * 
     * @param connectionId 连接ID
     * @param status 新状态
     * @param errorMessage 错误信息（可选）
     */
    @Async
    public void updateConnectionStatus(String connectionId, ConnectionInfo.ConnectionStatus status, 
                                     String errorMessage) {
        try {
            int updated = connectionInfoRepository.updateConnectionStatus(connectionId, status, errorMessage);
            
            if (updated == 0) {
                logger.warn("更新连接状态失败，连接不存在: {}", connectionId);
            } else {
                logger.debug("连接状态更新成功: {} -> {}", connectionId, status);
            }
            
        } catch (Exception e) {
            logger.error("更新连接状态异常: {}", connectionId, e);
        }
    }
    
    /**
     * 查询指定时间范围内的连接
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 连接列表
     */
    @Transactional(readOnly = true)
    public List<ConnectionInfo> findConnectionsBetween(LocalDateTime startTime, LocalDateTime endTime) {
        return connectionInfoRepository.findConnectionsBetween(startTime, endTime);
    }
    
    /**
     * 获取流量统计
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 流量统计 [总接收字节数, 总发送字节数, 总接收包数, 总发送包数]
     */
    @Transactional(readOnly = true)
    public TrafficStatistics getTrafficStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        Object[] stats = connectionInfoRepository.getTrafficStatsBetween(startTime, endTime);
        
        TrafficStatistics statistics = new TrafficStatistics();
        if (stats != null && stats.length >= 4) {
            statistics.setBytesReceived(stats[0] != null ? (Long) stats[0] : 0L);
            statistics.setBytesSent(stats[1] != null ? (Long) stats[1] : 0L);
            statistics.setPacketsReceived(stats[2] != null ? (Long) stats[2] : 0L);
            statistics.setPacketsSent(stats[3] != null ? (Long) stats[3] : 0L);
        }
        
        return statistics;
    }
    
    /**
     * 查询最近的连接记录
     *
     * @param limit 限制数量
     * @return 最近的连接记录
     */
    @Transactional(readOnly = true)
    public List<ConnectionInfo> findRecentConnections(int limit) {
        List<ConnectionInfo> allConnections = connectionInfoRepository.findRecentConnections();
        return allConnections.size() > limit ? allConnections.subList(0, limit) : allConnections;
    }
    
    /**
     * 定时清理超时连接
     */
    @Scheduled(fixedDelay = 60000) // 每分钟执行一次
    public void cleanupTimeoutConnections() {
        try {
            LocalDateTime timeoutThreshold = LocalDateTime.now().minusSeconds(idleTimeout / 1000);
            List<ConnectionInfo> timeoutConnections = connectionInfoRepository.findTimeoutConnections(timeoutThreshold);
            
            if (!timeoutConnections.isEmpty()) {
                logger.info("发现超时连接: {} 个", timeoutConnections.size());
                
                for (ConnectionInfo connection : timeoutConnections) {
                    connection.setStatus(ConnectionInfo.ConnectionStatus.TIMEOUT);
                    connection.setDisconnectedAt(LocalDateTime.now());
                    connectionInfoRepository.save(connection);
                }
                
                logger.info("超时连接清理完成: {} 个", timeoutConnections.size());
            }
            
        } catch (Exception e) {
            logger.error("清理超时连接异常", e);
        }
    }
    
    /**
     * 定时清理历史连接记录
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    public void cleanupHistoryConnections() {
        try {
            LocalDateTime beforeTime = LocalDateTime.now().minusDays(retentionDays);
            int deletedCount = connectionInfoRepository.deleteHistoryConnectionsBefore(beforeTime);
            
            if (deletedCount > 0) {
                logger.info("历史连接记录清理完成: {} 条", deletedCount);
            }
            
        } catch (Exception e) {
            logger.error("清理历史连接记录异常", e);
        }
    }
    
    /**
     * 获取连接统计信息
     * 
     * @return 连接统计信息
     */
    @Transactional(readOnly = true)
    public ConnectionStatistics getConnectionStatistics() {
        long activeConnections = connectionInfoRepository.countActiveConnections();
        List<Object[]> protocolStats = connectionInfoRepository.countActiveConnectionsByProtocol();
        
        ConnectionStatistics statistics = new ConnectionStatistics();
        statistics.setActiveConnections(activeConnections);
        
        for (Object[] stat : protocolStats) {
            ForwardRule.ProtocolType protocol = (ForwardRule.ProtocolType) stat[0];
            Long count = (Long) stat[1];
            
            switch (protocol) {
                case TCP:
                    statistics.setTcpConnections(count);
                    break;
                case UDP:
                    statistics.setUdpConnections(count);
                    break;
            }
        }
        
        return statistics;
    }
    
    /**
     * 流量统计信息类
     */
    public static class TrafficStatistics {
        private long bytesReceived;
        private long bytesSent;
        private long packetsReceived;
        private long packetsSent;
        
        // Getters and Setters
        public long getBytesReceived() { return bytesReceived; }
        public void setBytesReceived(long bytesReceived) { this.bytesReceived = bytesReceived; }
        
        public long getBytesSent() { return bytesSent; }
        public void setBytesSent(long bytesSent) { this.bytesSent = bytesSent; }
        
        public long getPacketsReceived() { return packetsReceived; }
        public void setPacketsReceived(long packetsReceived) { this.packetsReceived = packetsReceived; }
        
        public long getPacketsSent() { return packetsSent; }
        public void setPacketsSent(long packetsSent) { this.packetsSent = packetsSent; }
        
        public long getTotalBytes() { return bytesReceived + bytesSent; }
        public long getTotalPackets() { return packetsReceived + packetsSent; }
    }
    
    /**
     * 连接统计信息类
     */
    public static class ConnectionStatistics {
        private long activeConnections;
        private long tcpConnections;
        private long udpConnections;
        
        // Getters and Setters
        public long getActiveConnections() { return activeConnections; }
        public void setActiveConnections(long activeConnections) { this.activeConnections = activeConnections; }
        
        public long getTcpConnections() { return tcpConnections; }
        public void setTcpConnections(long tcpConnections) { this.tcpConnections = tcpConnections; }
        
        public long getUdpConnections() { return udpConnections; }
        public void setUdpConnections(long udpConnections) { this.udpConnections = udpConnections; }
    }

    /**
     * 根据规则ID查找活跃连接
     *
     * @param ruleId 规则ID
     * @return 活跃连接列表
     */
    @Transactional(readOnly = true)
    public List<ConnectionInfo> findActiveConnectionsByRuleId(Long ruleId) {
        return connectionInfoRepository.findByRuleIdAndStatus(ruleId, ConnectionInfo.ConnectionStatus.CONNECTED);
    }

    /**
     * 删除连接记录
     *
     * @param connectionId 连接ID
     */
    @Transactional
    public void deleteConnection(Long connectionId) {
        connectionInfoRepository.deleteById(connectionId);
        logger.debug("删除连接记录: {}", connectionId);
    }

    /**
     * 清空所有连接记录（危险操作，包括活跃连接）
     */
    @Transactional
    public void clearAllConnections() {
        connectionInfoRepository.deleteAll();
        logger.info("清空所有连接记录");
    }

    /**
     * 清除历史连接记录（只删除非活跃连接）
     */
    @Transactional
    public int clearHistoryConnections() {
        List<ConnectionInfo> historyConnections = connectionInfoRepository.findByStatusNot(ConnectionInfo.ConnectionStatus.CONNECTED);
        int count = historyConnections.size();

        if (count > 0) {
            connectionInfoRepository.deleteAll(historyConnections);
            logger.info("清除历史连接记录: {} 条", count);
        } else {
            logger.info("没有历史连接记录需要清除");
        }

        return count;
    }

    /**
     * 清除指定规则的历史连接记录
     */
    @Transactional
    public int clearHistoryConnectionsByRuleId(Long ruleId) {
        List<ConnectionInfo> historyConnections = connectionInfoRepository.findByRuleIdAndStatusNot(ruleId, ConnectionInfo.ConnectionStatus.CONNECTED);
        int count = historyConnections.size();

        if (count > 0) {
            connectionInfoRepository.deleteAll(historyConnections);
            logger.info("清除规则[{}]的历史连接记录: {} 条", ruleId, count);
        } else {
            logger.info("规则[{}]没有历史连接记录需要清除", ruleId);
        }

        return count;
    }

    /**
     * 获取历史连接记录统计
     */
    @Transactional(readOnly = true)
    public HistoryConnectionStats getHistoryConnectionStats() {
        long totalConnections = connectionInfoRepository.count();
        long activeConnections = connectionInfoRepository.countByStatus(ConnectionInfo.ConnectionStatus.CONNECTED);
        long historyConnections = totalConnections - activeConnections;

        return new HistoryConnectionStats(totalConnections, activeConnections, historyConnections);
    }

    /**
     * 历史连接统计信息类
     */
    public static class HistoryConnectionStats {
        private final long totalConnections;
        private final long activeConnections;
        private final long historyConnections;

        public HistoryConnectionStats(long totalConnections, long activeConnections, long historyConnections) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.historyConnections = historyConnections;
        }

        public long getTotalConnections() { return totalConnections; }
        public long getActiveConnections() { return activeConnections; }
        public long getHistoryConnections() { return historyConnections; }
    }
}
