package com.ux.relay.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 连接信息实体类
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Entity
@Table(name = "connection_info")
public class ConnectionInfo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 连接ID（唯一标识）
     */
    @Column(name = "connection_id", nullable = false, unique = true, length = 64)
    private String connectionId;
    
    /**
     * 关联的转发规则ID
     */
    @Column(name = "rule_id", nullable = false)
    private Long ruleId;
    
    /**
     * 协议类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 10)
    private ForwardRule.ProtocolType protocol;
    
    /**
     * 本地端口
     */
    @Column(name = "local_port", nullable = false)
    private Integer localPort;
    
    /**
     * 远端地址
     */
    @Column(name = "remote_address", nullable = false, length = 100)
    private String remoteAddress;
    
    /**
     * 远端端口
     */
    @Column(name = "remote_port", nullable = false)
    private Integer remotePort;
    
    /**
     * 连接状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConnectionStatus status;
    
    /**
     * 连接建立时间
     */
    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;
    
    /**
     * 连接断开时间
     */
    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;
    
    /**
     * 持续时间（毫秒）
     */
    @Column(name = "duration")
    private Long duration;
    
    /**
     * 接收字节数
     */
    @Column(name = "bytes_received", nullable = false)
    private Long bytesReceived = 0L;
    
    /**
     * 发送字节数
     */
    @Column(name = "bytes_sent", nullable = false)
    private Long bytesSent = 0L;
    
    /**
     * 接收包数
     */
    @Column(name = "packets_received", nullable = false)
    private Long packetsReceived = 0L;
    
    /**
     * 发送包数
     */
    @Column(name = "packets_sent", nullable = false)
    private Long packetsSent = 0L;
    
    /**
     * 最后活跃时间
     */
    @Column(name = "last_active_at")
    private LocalDateTime lastActiveAt;
    
    /**
     * 错误信息
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    
    // 连接状态枚举
    public enum ConnectionStatus {
        CONNECTING,    // 连接中
        CONNECTED,     // 已连接
        DISCONNECTED,  // 已断开
        ERROR,         // 错误
        TIMEOUT        // 超时
    }
    
    @PrePersist
    protected void onCreate() {
        if (connectedAt == null) {
            connectedAt = LocalDateTime.now();
        }
        lastActiveAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        if (status == ConnectionStatus.DISCONNECTED && disconnectedAt == null) {
            disconnectedAt = LocalDateTime.now();
            if (connectedAt != null) {
                duration = java.time.Duration.between(connectedAt, disconnectedAt).toMillis();
            }
        }
    }
    
    // Constructors
    public ConnectionInfo() {}
    
    public ConnectionInfo(String connectionId, Long ruleId, ForwardRule.ProtocolType protocol,
                         Integer localPort, String remoteAddress, Integer remotePort) {
        this.connectionId = connectionId;
        this.ruleId = ruleId;
        this.protocol = protocol;
        this.localPort = localPort;
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
        this.status = ConnectionStatus.CONNECTING;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getConnectionId() {
        return connectionId;
    }
    
    public void setConnectionId(String connectionId) {
        this.connectionId = connectionId;
    }
    
    public Long getRuleId() {
        return ruleId;
    }
    
    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }
    
    public ForwardRule.ProtocolType getProtocol() {
        return protocol;
    }
    
    public void setProtocol(ForwardRule.ProtocolType protocol) {
        this.protocol = protocol;
    }
    
    public Integer getLocalPort() {
        return localPort;
    }
    
    public void setLocalPort(Integer localPort) {
        this.localPort = localPort;
    }
    
    public String getRemoteAddress() {
        return remoteAddress;
    }
    
    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }
    
    public Integer getRemotePort() {
        return remotePort;
    }
    
    public void setRemotePort(Integer remotePort) {
        this.remotePort = remotePort;
    }
    
    public ConnectionStatus getStatus() {
        return status;
    }
    
    public void setStatus(ConnectionStatus status) {
        this.status = status;
    }
    
    public LocalDateTime getConnectedAt() {
        return connectedAt;
    }
    
    public void setConnectedAt(LocalDateTime connectedAt) {
        this.connectedAt = connectedAt;
    }
    
    public LocalDateTime getDisconnectedAt() {
        return disconnectedAt;
    }
    
    public void setDisconnectedAt(LocalDateTime disconnectedAt) {
        this.disconnectedAt = disconnectedAt;
    }
    
    public Long getDuration() {
        return duration;
    }
    
    public void setDuration(Long duration) {
        this.duration = duration;
    }
    
    public Long getBytesReceived() {
        return bytesReceived;
    }
    
    public void setBytesReceived(Long bytesReceived) {
        this.bytesReceived = bytesReceived;
    }
    
    public Long getBytesSent() {
        return bytesSent;
    }
    
    public void setBytesSent(Long bytesSent) {
        this.bytesSent = bytesSent;
    }
    
    public Long getPacketsReceived() {
        return packetsReceived;
    }
    
    public void setPacketsReceived(Long packetsReceived) {
        this.packetsReceived = packetsReceived;
    }
    
    public Long getPacketsSent() {
        return packetsSent;
    }
    
    public void setPacketsSent(Long packetsSent) {
        this.packetsSent = packetsSent;
    }
    
    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }
    
    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    @Override
    public String toString() {
        return "ConnectionInfo{" +
                "id=" + id +
                ", connectionId='" + connectionId + '\'' +
                ", ruleId=" + ruleId +
                ", protocol=" + protocol +
                ", localPort=" + localPort +
                ", remoteAddress='" + remoteAddress + '\'' +
                ", remotePort=" + remotePort +
                ", status=" + status +
                ", connectedAt=" + connectedAt +
                ", duration=" + duration +
                ", bytesReceived=" + bytesReceived +
                ", bytesSent=" + bytesSent +
                '}';
    }
}
