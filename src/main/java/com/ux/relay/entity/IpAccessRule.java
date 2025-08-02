package com.ux.relay.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * IP访问控制规则实体
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Entity
@Table(name = "ip_access_rule")
public class IpAccessRule {
    
    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 转发规则ID（为空表示全局规则）
     */
    @Column(name = "rule_id")
    private Long ruleId;
    
    /**
     * IP地址或CIDR网段
     */
    @Column(name = "ip_address", nullable = false, length = 50)
    private String ipAddress;
    
    /**
     * 访问类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false)
    private AccessType accessType;
    
    /**
     * 规则描述
     */
    @Column(name = "description", length = 200)
    private String description;
    
    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;
    
    /**
     * 优先级（数字越小优先级越高）
     */
    @Column(name = "priority", nullable = false)
    private Integer priority = 100;
    
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
     * 访问类型枚举
     */
    public enum AccessType {
        ALLOW,  // 白名单
        DENY    // 黑名单
    }
    
    // 构造函数
    public IpAccessRule() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public IpAccessRule(String ipAddress, AccessType accessType, String description) {
        this();
        this.ipAddress = ipAddress;
        this.accessType = accessType;
        this.description = description;
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
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public AccessType getAccessType() { return accessType; }
    public void setAccessType(AccessType accessType) { this.accessType = accessType; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    
    /**
     * 检查IP是否匹配此规则
     */
    public boolean matches(String clientIp) {
        if (!enabled) {
            return false;
        }
        
        try {
            // 如果是单个IP地址
            if (!ipAddress.contains("/")) {
                return ipAddress.equals(clientIp);
            }
            
            // 如果是CIDR网段
            return isIpInCidr(clientIp, ipAddress);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 检查IP是否在CIDR网段内
     */
    private boolean isIpInCidr(String ip, String cidr) {
        try {
            String[] cidrParts = cidr.split("/");
            String networkIp = cidrParts[0];
            int prefixLength = Integer.parseInt(cidrParts[1]);
            
            long ipLong = ipToLong(ip);
            long networkLong = ipToLong(networkIp);
            long mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
            
            return (ipLong & mask) == (networkLong & mask);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 将IP地址转换为长整型
     */
    private long ipToLong(String ip) {
        String[] parts = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) + Integer.parseInt(parts[i]);
        }
        return result;
    }
    
    @Override
    public String toString() {
        return String.format("IpAccessRule{id=%d, ipAddress='%s', accessType=%s, enabled=%s}", 
                           id, ipAddress, accessType, enabled);
    }
}
