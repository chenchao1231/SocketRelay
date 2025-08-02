package com.ux.relay.entity;

import javax.persistence.*;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * 转发规则实体类
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Entity
@Table(name = "forward_rules")
public class ForwardRule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 规则名称
     */
    @NotBlank(message = "规则名称不能为空")
    @Column(name = "rule_name", nullable = false, length = 100)
    private String ruleName;
    
    /**
     * 源IP地址（可选，为空表示监听所有IP）
     */
    @Column(name = "source_ip", length = 45)
    private String sourceIp;
    
    /**
     * 源端口
     */
    @NotNull(message = "源端口不能为空")
    @Min(value = 1, message = "端口号必须在1-65535之间")
    @Max(value = 65535, message = "端口号必须在1-65535之间")
    @Column(name = "source_port", nullable = false)
    private Integer sourcePort;
    
    /**
     * 目标IP地址
     */
    @NotBlank(message = "目标IP地址不能为空")
    @Column(name = "target_ip", nullable = false, length = 45)
    private String targetIp;
    
    /**
     * 目标端口
     */
    @NotNull(message = "目标端口不能为空")
    @Min(value = 1, message = "端口号必须在1-65535之间")
    @Max(value = 65535, message = "端口号必须在1-65535之间")
    @Column(name = "target_port", nullable = false)
    private Integer targetPort;
    
    /**
     * 协议类型：TCP, UDP, TCP_UDP
     */
    @NotNull(message = "协议类型不能为空")
    @Enumerated(EnumType.STRING)
    @Column(name = "protocol", nullable = false, length = 10)
    private ProtocolType protocol;
    
    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
    
    /**
     * 备注信息
     */
    @Column(name = "remark", length = 500)
    private String remark;
    
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
     * 创建者
     */
    @Column(name = "created_by", length = 50)
    private String createdBy;
    
    /**
     * 更新者
     */
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    /**
     * 数据源连接地址名称
     */
    @Column(name = "data_source_name", length = 100)
    private String dataSourceName;

    /**
     * 是否启用自动重连
     */
    @Column(name = "auto_reconnect", nullable = false)
    private Boolean autoReconnect = true;

    /**
     * 重连间隔时间（毫秒）
     */
    @Column(name = "reconnect_interval", nullable = false)
    private Long reconnectInterval = 5000L;

    /**
     * 最大重连次数
     */
    @Column(name = "max_reconnect_attempts", nullable = false)
    private Integer maxReconnectAttempts = 10;

    /**
     * 连接池大小
     */
    @Column(name = "connection_pool_size", nullable = false)
    private Integer connectionPoolSize = 1;
    
    // 协议类型枚举
    public enum ProtocolType {
        TCP, UDP, TCP_UDP
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Constructors
    public ForwardRule() {}
    
    public ForwardRule(String ruleName, String sourceIp, Integer sourcePort, 
                      String targetIp, Integer targetPort, ProtocolType protocol) {
        this.ruleName = ruleName;
        this.sourceIp = sourceIp;
        this.sourcePort = sourcePort;
        this.targetIp = targetIp;
        this.targetPort = targetPort;
        this.protocol = protocol;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getRuleName() {
        return ruleName;
    }
    
    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }
    
    public String getSourceIp() {
        return sourceIp;
    }
    
    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }
    
    public Integer getSourcePort() {
        return sourcePort;
    }
    
    public void setSourcePort(Integer sourcePort) {
        this.sourcePort = sourcePort;
    }
    
    public String getTargetIp() {
        return targetIp;
    }
    
    public void setTargetIp(String targetIp) {
        this.targetIp = targetIp;
    }
    
    public Integer getTargetPort() {
        return targetPort;
    }
    
    public void setTargetPort(Integer targetPort) {
        this.targetPort = targetPort;
    }
    
    public ProtocolType getProtocol() {
        return protocol;
    }
    
    public void setProtocol(ProtocolType protocol) {
        this.protocol = protocol;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getRemark() {
        return remark;
    }
    
    public void setRemark(String remark) {
        this.remark = remark;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public String getCreatedBy() {
        return createdBy;
    }
    
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public String getUpdatedBy() {
        return updatedBy;
    }
    
    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getDataSourceName() {
        return dataSourceName;
    }

    public void setDataSourceName(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    public Boolean getAutoReconnect() {
        return autoReconnect;
    }

    public void setAutoReconnect(Boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public Long getReconnectInterval() {
        return reconnectInterval;
    }

    public void setReconnectInterval(Long reconnectInterval) {
        this.reconnectInterval = reconnectInterval;
    }

    public Integer getMaxReconnectAttempts() {
        return maxReconnectAttempts;
    }

    public void setMaxReconnectAttempts(Integer maxReconnectAttempts) {
        this.maxReconnectAttempts = maxReconnectAttempts;
    }

    public Integer getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public void setConnectionPoolSize(Integer connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
    }
    
    @Override
    public String toString() {
        return "ForwardRule{" +
                "id=" + id +
                ", ruleName='" + ruleName + '\'' +
                ", sourceIp='" + sourceIp + '\'' +
                ", sourcePort=" + sourcePort +
                ", targetIp='" + targetIp + '\'' +
                ", targetPort=" + targetPort +
                ", protocol=" + protocol +
                ", enabled=" + enabled +
                ", remark='" + remark + '\'' +
                '}';
    }
}
