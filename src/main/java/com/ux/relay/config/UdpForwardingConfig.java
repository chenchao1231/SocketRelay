package com.ux.relay.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * UDP转发配置类
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-04
 */
@Component
@ConfigurationProperties(prefix = "app.forwarding.udp.forwarding")
public class UdpForwardingConfig {

    private static final Logger logger = LoggerFactory.getLogger(UdpForwardingConfig.class);

    /**
     * 转发模式: point-to-point(点对点) 或 broadcast(广播)
     */
    private String mode = "point-to-point";

    @PostConstruct
    public void init() {
        logger.info("UDP转发配置初始化 - 模式: {}", mode);
    }
    
    /**
     * 广播模式配置
     */
    private BroadcastConfig broadcast = new BroadcastConfig();
    
    public String getMode() {
        return mode;
    }
    
    public void setMode(String mode) {
        this.mode = mode;
    }
    
    public BroadcastConfig getBroadcast() {
        return broadcast;
    }
    
    public void setBroadcast(BroadcastConfig broadcast) {
        this.broadcast = broadcast;
    }
    
    /**
     * 广播模式配置
     */
    public static class BroadcastConfig {
        
        /**
         * 是否启用订阅机制
         */
        private boolean enableSubscription = true;
        
        /**
         * 客户端超时时间（毫秒）
         */
        private long clientTimeoutMs = 300000L;
        
        /**
         * 心跳检查间隔（毫秒）
         */
        private long heartbeatIntervalMs = 60000L;
        
        public boolean isEnableSubscription() {
            return enableSubscription;
        }
        
        public void setEnableSubscription(boolean enableSubscription) {
            this.enableSubscription = enableSubscription;
        }
        
        public long getClientTimeoutMs() {
            return clientTimeoutMs;
        }
        
        public void setClientTimeoutMs(long clientTimeoutMs) {
            this.clientTimeoutMs = clientTimeoutMs;
        }
        
        public long getHeartbeatIntervalMs() {
            return heartbeatIntervalMs;
        }
        
        public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
            this.heartbeatIntervalMs = heartbeatIntervalMs;
        }
    }
}
