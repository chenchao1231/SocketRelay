package com.ux.relay.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 审计日志实体类
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * 操作类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private ActionType actionType;
    
    /**
     * 操作用户
     */
    @Column(name = "username", nullable = false, length = 50)
    private String username;
    
    /**
     * 操作描述
     */
    @Column(name = "description", nullable = false, length = 500)
    private String description;
    
    /**
     * 操作对象类型
     */
    @Column(name = "target_type", length = 50)
    private String targetType;
    
    /**
     * 操作对象ID
     */
    @Column(name = "target_id", length = 100)
    private String targetId;
    
    /**
     * 客户端IP地址
     */
    @Column(name = "client_ip", length = 45)
    private String clientIp;
    
    /**
     * 用户代理
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;
    
    /**
     * 操作结果
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private OperationResult result;
    
    /**
     * 错误信息
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;
    
    /**
     * 操作前数据（JSON格式）
     */
    @Column(name = "before_data", columnDefinition = "TEXT")
    private String beforeData;
    
    /**
     * 操作后数据（JSON格式）
     */
    @Column(name = "after_data", columnDefinition = "TEXT")
    private String afterData;
    
    /**
     * 操作时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * 操作耗时（毫秒）
     */
    @Column(name = "duration")
    private Long duration;
    
    // 操作类型枚举
    public enum ActionType {
        LOGIN,              // 登录
        LOGOUT,             // 登出
        CREATE_RULE,        // 创建转发规则
        UPDATE_RULE,        // 更新转发规则
        DELETE_RULE,        // 删除转发规则
        ENABLE_RULE,        // 启用转发规则
        DISABLE_RULE,       // 禁用转发规则
        START_FORWARDING,   // 启动转发
        STOP_FORWARDING,    // 停止转发
        CONNECTION_ESTABLISHED, // 连接建立
        CONNECTION_CLOSED,  // 连接关闭
        SYSTEM_START,       // 系统启动
        SYSTEM_SHUTDOWN,    // 系统关闭
        CONFIG_CHANGE,      // 配置变更
        EXPORT_DATA,        // 数据导出
        IMPORT_DATA         // 数据导入
    }
    
    // 操作结果枚举
    public enum OperationResult {
        SUCCESS,    // 成功
        FAILURE,    // 失败
        PARTIAL     // 部分成功
    }
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    // Constructors
    public AuditLog() {}
    
    public AuditLog(ActionType actionType, String username, String description) {
        this.actionType = actionType;
        this.username = username;
        this.description = description;
        this.result = OperationResult.SUCCESS;
    }
    
    public AuditLog(ActionType actionType, String username, String description, 
                   OperationResult result) {
        this.actionType = actionType;
        this.username = username;
        this.description = description;
        this.result = result;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public ActionType getActionType() {
        return actionType;
    }
    
    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getTargetType() {
        return targetType;
    }
    
    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }
    
    public String getTargetId() {
        return targetId;
    }
    
    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }
    
    public String getClientIp() {
        return clientIp;
    }
    
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    public String getUserAgent() {
        return userAgent;
    }
    
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    public OperationResult getResult() {
        return result;
    }
    
    public void setResult(OperationResult result) {
        this.result = result;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public String getBeforeData() {
        return beforeData;
    }
    
    public void setBeforeData(String beforeData) {
        this.beforeData = beforeData;
    }
    
    public String getAfterData() {
        return afterData;
    }
    
    public void setAfterData(String afterData) {
        this.afterData = afterData;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public Long getDuration() {
        return duration;
    }
    
    public void setDuration(Long duration) {
        this.duration = duration;
    }
    
    @Override
    public String toString() {
        return "AuditLog{" +
                "id=" + id +
                ", actionType=" + actionType +
                ", username='" + username + '\'' +
                ", description='" + description + '\'' +
                ", targetType='" + targetType + '\'' +
                ", targetId='" + targetId + '\'' +
                ", clientIp='" + clientIp + '\'' +
                ", result=" + result +
                ", createdAt=" + createdAt +
                '}';
    }
}
