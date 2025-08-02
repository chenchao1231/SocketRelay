package com.ux.relay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ux.relay.entity.AuditLog;
import com.ux.relay.repository.AuditLogRepository;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计服务
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Service
@Transactional
public class AuditService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${app.audit.retention-days:30}")
    private int retentionDays;
    
    /**
     * 记录操作日志
     * 
     * @param actionType 操作类型
     * @param username 用户名
     * @param description 操作描述
     */
    @Async
    public void logAction(AuditLog.ActionType actionType, String username, String description) {
        logAction(actionType, username, description, null, null, null, null, AuditLog.OperationResult.SUCCESS);
    }
    
    /**
     * 记录操作日志
     * 
     * @param actionType 操作类型
     * @param username 用户名
     * @param description 操作描述
     * @param targetType 操作对象类型
     * @param targetId 操作对象ID
     */
    @Async
    public void logAction(AuditLog.ActionType actionType, String username, String description, 
                         String targetType, String targetId) {
        logAction(actionType, username, description, targetType, targetId, null, null, AuditLog.OperationResult.SUCCESS);
    }
    
    /**
     * 记录操作日志
     * 
     * @param actionType 操作类型
     * @param username 用户名
     * @param description 操作描述
     * @param result 操作结果
     */
    @Async
    public void logAction(AuditLog.ActionType actionType, String username, String description, 
                         AuditLog.OperationResult result) {
        logAction(actionType, username, description, null, null, null, null, result);
    }
    
    /**
     * 记录操作日志（完整版本）
     * 
     * @param actionType 操作类型
     * @param username 用户名
     * @param description 操作描述
     * @param targetType 操作对象类型
     * @param targetId 操作对象ID
     * @param beforeData 操作前数据
     * @param afterData 操作后数据
     * @param result 操作结果
     */
    @Async
    public void logAction(AuditLog.ActionType actionType, String username, String description,
                         String targetType, String targetId, Object beforeData, Object afterData,
                         AuditLog.OperationResult result) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setActionType(actionType);
            auditLog.setUsername(username);
            auditLog.setDescription(description);
            auditLog.setTargetType(targetType);
            auditLog.setTargetId(targetId);
            auditLog.setResult(result);
            
            // 获取请求信息
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setClientIp(getClientIpAddress(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            }
            
            // 序列化数据对象
            if (beforeData != null) {
                auditLog.setBeforeData(serializeObject(beforeData));
            }
            if (afterData != null) {
                auditLog.setAfterData(serializeObject(afterData));
            }
            
            // 保存审计日志
            auditLogRepository.save(auditLog);
            
            logger.debug("审计日志记录成功: {} - {}", actionType, description);
            
        } catch (Exception e) {
            logger.error("记录审计日志异常", e);
        }
    }
    
    /**
     * 记录失败操作日志
     * 
     * @param actionType 操作类型
     * @param username 用户名
     * @param description 操作描述
     * @param errorMessage 错误信息
     */
    @Async
    public void logFailure(AuditLog.ActionType actionType, String username, String description, String errorMessage) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setActionType(actionType);
            auditLog.setUsername(username);
            auditLog.setDescription(description);
            auditLog.setResult(AuditLog.OperationResult.FAILURE);
            auditLog.setErrorMessage(errorMessage);
            
            // 获取请求信息
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setClientIp(getClientIpAddress(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            }
            
            auditLogRepository.save(auditLog);
            
            logger.debug("失败操作日志记录成功: {} - {}", actionType, description);
            
        } catch (Exception e) {
            logger.error("记录失败操作日志异常", e);
        }
    }
    
    /**
     * 分页查询审计日志
     * 
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findAll(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }
    
    /**
     * 根据用户名查询审计日志
     * 
     * @param username 用户名
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByUsername(String username, Pageable pageable) {
        return auditLogRepository.findByUsername(username, pageable);
    }
    
    /**
     * 根据操作类型查询审计日志
     * 
     * @param actionType 操作类型
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByActionType(AuditLog.ActionType actionType, Pageable pageable) {
        return auditLogRepository.findByActionType(actionType, pageable);
    }
    
    /**
     * 根据时间范围查询审计日志
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        return auditLogRepository.findByCreatedAtBetween(startTime, endTime, pageable);
    }
    
    /**
     * 多条件查询审计日志
     * 
     * @param username 用户名
     * @param actionType 操作类型
     * @param result 操作结果
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> findByConditions(String username, AuditLog.ActionType actionType, 
                                          AuditLog.OperationResult result, LocalDateTime startTime, 
                                          LocalDateTime endTime, Pageable pageable) {
        return auditLogRepository.findByConditions(username, actionType, result, startTime, endTime, pageable);
    }
    
    /**
     * 关键词搜索审计日志
     * 
     * @param keyword 关键词
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> searchByKeyword(String keyword, Pageable pageable) {
        return auditLogRepository.findByKeyword(keyword, pageable);
    }
    
    /**
     * 获取审计统计信息
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 审计统计信息
     */
    @Transactional(readOnly = true)
    public AuditStatistics getAuditStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        long totalOperations = auditLogRepository.countByCreatedAtBetween(startTime, endTime);
        long failureOperations = auditLogRepository.countFailureOperations(startTime, endTime);
        List<Object[]> actionTypeStats = auditLogRepository.countByActionType(startTime, endTime);
        List<Object[]> userStats = auditLogRepository.countByUsername(startTime, endTime);
        
        AuditStatistics statistics = new AuditStatistics();
        statistics.setTotalOperations(totalOperations);
        statistics.setSuccessOperations(totalOperations - failureOperations);
        statistics.setFailureOperations(failureOperations);
        statistics.setActionTypeStats(actionTypeStats);
        statistics.setUserStats(userStats);
        
        return statistics;
    }
    
    /**
     * 查询最近的审计日志
     *
     * @param limit 限制数量
     * @return 最近的审计日志
     */
    @Transactional(readOnly = true)
    public List<AuditLog> findRecentLogs(int limit) {
        List<AuditLog> allLogs = auditLogRepository.findRecentLogs();
        return allLogs.size() > limit ? allLogs.subList(0, limit) : allLogs;
    }
    
    /**
     * 定时清理过期的审计日志
     */
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点执行
    public void cleanupExpiredLogs() {
        try {
            LocalDateTime beforeTime = LocalDateTime.now().minusDays(retentionDays);
            int deletedCount = auditLogRepository.deleteLogsBefore(beforeTime);
            
            if (deletedCount > 0) {
                logger.info("过期审计日志清理完成: {} 条", deletedCount);
            }
            
        } catch (Exception e) {
            logger.error("清理过期审计日志异常", e);
        }
    }
    
    /**
     * 获取客户端IP地址
     * 
     * @param request HTTP请求
     * @return 客户端IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * 序列化对象为JSON字符串
     * 
     * @param object 对象
     * @return JSON字符串
     */
    private String serializeObject(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            logger.warn("对象序列化失败", e);
            return object.toString();
        }
    }
    
    /**
     * 审计统计信息类
     */
    public static class AuditStatistics {
        private long totalOperations;
        private long successOperations;
        private long failureOperations;
        private List<Object[]> actionTypeStats;
        private List<Object[]> userStats;
        
        // Getters and Setters
        public long getTotalOperations() { return totalOperations; }
        public void setTotalOperations(long totalOperations) { this.totalOperations = totalOperations; }
        
        public long getSuccessOperations() { return successOperations; }
        public void setSuccessOperations(long successOperations) { this.successOperations = successOperations; }
        
        public long getFailureOperations() { return failureOperations; }
        public void setFailureOperations(long failureOperations) { this.failureOperations = failureOperations; }
        
        public List<Object[]> getActionTypeStats() { return actionTypeStats; }
        public void setActionTypeStats(List<Object[]> actionTypeStats) { this.actionTypeStats = actionTypeStats; }
        
        public List<Object[]> getUserStats() { return userStats; }
        public void setUserStats(List<Object[]> userStats) { this.userStats = userStats; }
        
        public double getSuccessRate() {
            return totalOperations > 0 ? (double) successOperations / totalOperations * 100 : 0.0;
        }
    }
}
