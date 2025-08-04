package com.ux.relay.core;

import com.ux.relay.entity.ForwardRule;
import com.ux.relay.service.ConnectionService;
import com.ux.relay.service.MetricsService;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP广播转发管理器
 * 管理所有UDP广播转发服务的生命周期
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-04
 */
@Component
public class UdpBroadcastManager {
    
    private static final Logger logger = LoggerFactory.getLogger(UdpBroadcastManager.class);
    
    @Autowired
    private ConnectionService connectionService;
    
    @Autowired
    private MetricsService metricsService;
    
    @Autowired
    private UdpSessionManager udpSessionManager;
    
    // 活跃的UDP广播转发服务
    private final Map<Long, UdpBroadcastForwardingHandler> activeHandlers = new ConcurrentHashMap<>();
    
    /**
     * 启动UDP广播转发服务
     */
    public void startUdpBroadcast(ForwardRule rule, EventLoopGroup eventLoopGroup) throws Exception {
        if (activeHandlers.containsKey(rule.getId())) {
            logger.warn("UDP广播转发服务已存在: {}", rule.getId());
            return;
        }
        
        try {
            UdpBroadcastForwardingHandler handler = new UdpBroadcastForwardingHandler(
                rule, connectionService, metricsService, udpSessionManager);
            
            handler.start(eventLoopGroup);
            activeHandlers.put(rule.getId(), handler);
            
            logger.info("UDP广播转发服务启动成功: 规则ID={}, 下游端口={}, 上游端口={}",
                       rule.getId(), rule.getSourcePort(), rule.getTargetPort());
            
        } catch (Exception e) {
            logger.error("启动UDP广播转发服务失败: 规则ID={}", rule.getId(), e);
            throw e;
        }
    }
    
    /**
     * 停止UDP广播转发服务
     */
    public void stopUdpBroadcast(Long ruleId) {
        UdpBroadcastForwardingHandler handler = activeHandlers.remove(ruleId);
        if (handler != null) {
            try {
                handler.stop();
                logger.info("UDP广播转发服务已停止: 规则ID={}", ruleId);
            } catch (Exception e) {
                logger.error("停止UDP广播转发服务失败: 规则ID={}", ruleId, e);
            }
        } else {
            logger.warn("UDP广播转发服务不存在: 规则ID={}", ruleId);
        }
    }
    
    /**
     * 重启UDP广播转发服务
     */
    public void restartUdpBroadcast(ForwardRule rule, EventLoopGroup eventLoopGroup) throws Exception {
        stopUdpBroadcast(rule.getId());
        startUdpBroadcast(rule, eventLoopGroup);
    }
    
    /**
     * 获取活跃的UDP广播转发服务数量
     */
    public int getActiveHandlerCount() {
        return activeHandlers.size();
    }
    
    /**
     * 检查UDP广播转发服务是否活跃
     */
    public boolean isUdpBroadcastActive(Long ruleId) {
        return activeHandlers.containsKey(ruleId);
    }
    
    /**
     * 获取UDP广播转发处理器
     */
    public UdpBroadcastForwardingHandler getHandler(Long ruleId) {
        return activeHandlers.get(ruleId);
    }
    
    /**
     * 获取所有活跃的规则ID
     */
    public Long[] getActiveRuleIds() {
        return activeHandlers.keySet().toArray(new Long[0]);
    }
    
    /**
     * 关闭所有UDP广播转发服务
     */
    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭所有UDP广播转发服务...");
        
        int shutdownCount = 0;
        for (Map.Entry<Long, UdpBroadcastForwardingHandler> entry : activeHandlers.entrySet()) {
            try {
                entry.getValue().stop();
                shutdownCount++;
                logger.info("UDP广播转发服务已关闭: 规则ID={}", entry.getKey());
            } catch (Exception e) {
                logger.error("关闭UDP广播转发服务失败: 规则ID={}", entry.getKey(), e);
            }
        }
        
        activeHandlers.clear();
        logger.info("UDP广播转发管理器已关闭，共关闭{}个服务", shutdownCount);
    }
}
