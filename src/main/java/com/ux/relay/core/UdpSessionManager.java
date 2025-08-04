package com.ux.relay.core;

import com.ux.relay.entity.ConnectionInfo;
import com.ux.relay.service.ConnectionService;
import com.ux.relay.service.MetricsService;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UDP会话管理器
 * 负责管理UDP会话的生命周期，防止内存泄漏
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-04
 */
@Component
public class UdpSessionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(UdpSessionManager.class);
    
    // UDP会话超时时间（默认5分钟）
    private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000L;
    
    // 清理任务执行间隔（默认1分钟）
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000L;
    
    // 会话信息存储
    private final Map<String, UdpSession> sessions = new ConcurrentHashMap<>();
    
    // 定时清理任务
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "UDP-Session-Cleanup");
        t.setDaemon(true);
        return t;
    });
    
    // 统计信息
    private final AtomicLong totalSessions = new AtomicLong(0);
    private final AtomicLong activeSessions = new AtomicLong(0);
    private final AtomicLong expiredSessions = new AtomicLong(0);
    
    public UdpSessionManager() {
        // 启动定时清理任务
        cleanupExecutor.scheduleWithFixedDelay(
            this::cleanupExpiredSessions, 
            CLEANUP_INTERVAL_MS, 
            CLEANUP_INTERVAL_MS, 
            TimeUnit.MILLISECONDS
        );
        
        logger.info("UDP会话管理器已启动，会话超时: {}ms, 清理间隔: {}ms", 
                   SESSION_TIMEOUT_MS, CLEANUP_INTERVAL_MS);
    }
    
    /**
     * 创建或获取UDP会话
     */
    public UdpSession getOrCreateSession(String sessionKey, InetSocketAddress clientAddress, 
                                        Channel outboundChannel, ConnectionInfo connectionInfo) {
        UdpSession session = sessions.get(sessionKey);
        
        if (session != null && session.isValid()) {
            // 更新最后活跃时间
            session.updateLastActiveTime();
            return session;
        }
        
        // 创建新会话
        session = new UdpSession(sessionKey, clientAddress, outboundChannel, connectionInfo);
        UdpSession existingSession = sessions.putIfAbsent(sessionKey, session);
        
        if (existingSession == null) {
            // 新会话创建成功
            totalSessions.incrementAndGet();
            activeSessions.incrementAndGet();
            
            logger.debug("创建UDP会话: {}, 当前活跃会话数: {}", sessionKey, activeSessions.get());
            return session;
        } else {
            // 使用已存在的会话
            existingSession.updateLastActiveTime();
            return existingSession;
        }
    }
    
    /**
     * 移除会话
     */
    public void removeSession(String sessionKey) {
        UdpSession session = sessions.remove(sessionKey);
        if (session != null) {
            session.close();
            activeSessions.decrementAndGet();
            logger.debug("移除UDP会话: {}, 当前活跃会话数: {}", sessionKey, activeSessions.get());
        }
    }
    
    /**
     * 获取会话
     */
    public UdpSession getSession(String sessionKey) {
        UdpSession session = sessions.get(sessionKey);
        if (session != null && session.isValid()) {
            session.updateLastActiveTime();
            return session;
        }
        return null;
    }
    
    /**
     * 清理过期会话
     */
    public void cleanupExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        int cleanedCount = 0;
        
        Iterator<Map.Entry<String, UdpSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, UdpSession> entry = iterator.next();
            UdpSession session = entry.getValue();
            
            if (currentTime - session.getLastActiveTime() > SESSION_TIMEOUT_MS || !session.isValid()) {
                // 会话已过期或无效
                iterator.remove();
                session.close();
                activeSessions.decrementAndGet();
                expiredSessions.incrementAndGet();
                cleanedCount++;
                
                logger.debug("清理过期UDP会话: {}", entry.getKey());
            }
        }
        
        if (cleanedCount > 0) {
            logger.info("清理过期UDP会话: {}个, 当前活跃会话数: {}", cleanedCount, activeSessions.get());
        }
    }
    
    /**
     * 获取会话统计信息
     */
    public SessionStats getSessionStats() {
        return new SessionStats(
            totalSessions.get(),
            activeSessions.get(),
            expiredSessions.get(),
            sessions.size()
        );
    }
    
    /**
     * 生成会话键
     */
    public static String generateSessionKey(InetSocketAddress clientAddress, Long ruleId) {
        return String.format("%s:%d@%d", 
                           clientAddress.getHostString(), 
                           clientAddress.getPort(), 
                           ruleId);
    }
    
    /**
     * 关闭会话管理器
     */
    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭UDP会话管理器...");
        
        // 停止清理任务
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 关闭所有会话
        int closedCount = 0;
        for (UdpSession session : sessions.values()) {
            session.close();
            closedCount++;
        }
        sessions.clear();
        
        logger.info("UDP会话管理器已关闭，关闭了{}个会话", closedCount);
    }
    
    /**
     * UDP会话信息
     */
    public static class UdpSession {
        private final String sessionKey;
        private final InetSocketAddress clientAddress;
        private final Channel outboundChannel;
        private final ConnectionInfo connectionInfo;
        private final long createTime;
        private volatile long lastActiveTime;
        private volatile boolean closed = false;
        
        public UdpSession(String sessionKey, InetSocketAddress clientAddress, 
                         Channel outboundChannel, ConnectionInfo connectionInfo) {
            this.sessionKey = sessionKey;
            this.clientAddress = clientAddress;
            this.outboundChannel = outboundChannel;
            this.connectionInfo = connectionInfo;
            this.createTime = System.currentTimeMillis();
            this.lastActiveTime = createTime;
        }
        
        public void updateLastActiveTime() {
            this.lastActiveTime = System.currentTimeMillis();
        }
        
        public boolean isValid() {
            return !closed && outboundChannel != null && outboundChannel.isActive();
        }
        
        public void close() {
            if (!closed) {
                closed = true;
                if (outboundChannel != null && outboundChannel.isActive()) {
                    outboundChannel.close();
                }
            }
        }
        
        // Getters
        public String getSessionKey() { return sessionKey; }
        public InetSocketAddress getClientAddress() { return clientAddress; }
        public Channel getOutboundChannel() { return outboundChannel; }
        public ConnectionInfo getConnectionInfo() { return connectionInfo; }
        public long getCreateTime() { return createTime; }
        public long getLastActiveTime() { return lastActiveTime; }
        public boolean isClosed() { return closed; }
    }
    
    /**
     * 会话统计信息
     */
    public static class SessionStats {
        private final long totalSessions;
        private final long activeSessions;
        private final long expiredSessions;
        private final long currentSessions;
        
        public SessionStats(long totalSessions, long activeSessions, 
                           long expiredSessions, long currentSessions) {
            this.totalSessions = totalSessions;
            this.activeSessions = activeSessions;
            this.expiredSessions = expiredSessions;
            this.currentSessions = currentSessions;
        }
        
        // Getters
        public long getTotalSessions() { return totalSessions; }
        public long getActiveSessions() { return activeSessions; }
        public long getExpiredSessions() { return expiredSessions; }
        public long getCurrentSessions() { return currentSessions; }
        
        @Override
        public String toString() {
            return String.format("SessionStats{total=%d, active=%d, expired=%d, current=%d}", 
                               totalSessions, activeSessions, expiredSessions, currentSessions);
        }
    }
}
