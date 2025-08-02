package com.ux.relay.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 客户端监听状态实体
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Entity
@Table(name = "client_listener_status")
public class ClientListenerStatus {
    
    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 转发规则ID
     */
    @Column(name = "rule_id", nullable = false)
    private Long ruleId;
    
    /**
     * 监听端口
     */
    @Column(name = "listen_port", nullable = false)
    private Integer listenPort;
    
    /**
     * 协议类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false)
    private ForwardRule.ProtocolType protocol;
    
    /**
     * 监听状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ListenerStatus status;
    
    /**
     * 等待客户端连接开始时间
     */
    @Column(name = "waiting_since")
    private LocalDateTime waitingSince;
    
    /**
     * 最后一次客户端连接时间
     */
    @Column(name = "last_client_connected")
    private LocalDateTime lastClientConnected;
    
    /**
     * 当前客户端连接数
     */
    @Column(name = "current_client_count", nullable = false)
    private Integer currentClientCount = 0;
    
    /**
     * 总客户端连接数（历史累计）
     */
    @Column(name = "total_client_count", nullable = false)
    private Long totalClientCount = 0L;
    
    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    /**
     * 监听状态枚举
     */
    public enum ListenerStatus {
        STARTING,           // 启动中
        WAITING_CLIENT,     // 等待客户端连接
        ACTIVE,            // 有客户端连接
        STOPPED,           // 已停止
        ERROR              // 错误状态
    }
    
    // 构造函数
    public ClientListenerStatus() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public ClientListenerStatus(Long ruleId, Integer listenPort, ForwardRule.ProtocolType protocol) {
        this();
        this.ruleId = ruleId;
        this.listenPort = listenPort;
        this.protocol = protocol;
        this.status = ListenerStatus.STARTING;
        this.waitingSince = LocalDateTime.now();
    }
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    
    public Integer getListenPort() { return listenPort; }
    public void setListenPort(Integer listenPort) { this.listenPort = listenPort; }
    
    public ForwardRule.ProtocolType getProtocol() { return protocol; }
    public void setProtocol(ForwardRule.ProtocolType protocol) { this.protocol = protocol; }
    
    public ListenerStatus getStatus() { return status; }
    public void setStatus(ListenerStatus status) { this.status = status; }
    
    public LocalDateTime getWaitingSince() { return waitingSince; }
    public void setWaitingSince(LocalDateTime waitingSince) { this.waitingSince = waitingSince; }
    
    public LocalDateTime getLastClientConnected() { return lastClientConnected; }
    public void setLastClientConnected(LocalDateTime lastClientConnected) { this.lastClientConnected = lastClientConnected; }
    
    public Integer getCurrentClientCount() { return currentClientCount; }
    public void setCurrentClientCount(Integer currentClientCount) { this.currentClientCount = currentClientCount; }
    
    public Long getTotalClientCount() { return totalClientCount; }
    public void setTotalClientCount(Long totalClientCount) { this.totalClientCount = totalClientCount; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    /**
     * 客户端连接
     */
    public void onClientConnected() {
        this.currentClientCount++;
        this.totalClientCount++;
        this.lastClientConnected = LocalDateTime.now();
        this.status = ListenerStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 客户端断开
     */
    public void onClientDisconnected() {
        this.currentClientCount = Math.max(0, this.currentClientCount - 1);
        if (this.currentClientCount == 0) {
            this.status = ListenerStatus.WAITING_CLIENT;
            this.waitingSince = LocalDateTime.now();
        }
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 开始等待客户端
     */
    public void startWaiting() {
        this.status = ListenerStatus.WAITING_CLIENT;
        this.waitingSince = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 停止监听
     */
    public void stop() {
        this.status = ListenerStatus.STOPPED;
        this.currentClientCount = 0;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 设置错误状态
     */
    public void setError() {
        this.status = ListenerStatus.ERROR;
        this.updatedAt = LocalDateTime.now();
    }
}
