package com.ux.relay.repository;

import com.ux.relay.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志数据访问接口
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    /**
     * 根据用户名查询审计日志
     * 
     * @param username 用户名
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    Page<AuditLog> findByUsername(String username, Pageable pageable);
    
    /**
     * 根据操作类型查询审计日志
     * 
     * @param actionType 操作类型
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    Page<AuditLog> findByActionType(AuditLog.ActionType actionType, Pageable pageable);
    
    /**
     * 根据操作结果查询审计日志
     * 
     * @param result 操作结果
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    Page<AuditLog> findByResult(AuditLog.OperationResult result, Pageable pageable);
    
    /**
     * 根据时间范围查询审计日志
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :startTime AND :endTime ORDER BY a.createdAt DESC")
    Page<AuditLog> findByCreatedAtBetween(@Param("startTime") LocalDateTime startTime, 
                                         @Param("endTime") LocalDateTime endTime, 
                                         Pageable pageable);
    
    /**
     * 根据客户端IP查询审计日志
     * 
     * @param clientIp 客户端IP
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    Page<AuditLog> findByClientIp(String clientIp, Pageable pageable);
    
    /**
     * 多条件查询审计日志
     * 
     * @param username 用户名（可选）
     * @param actionType 操作类型（可选）
     * @param result 操作结果（可选）
     * @param startTime 开始时间（可选）
     * @param endTime 结束时间（可选）
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "(:username IS NULL OR a.username = :username) AND " +
           "(:actionType IS NULL OR a.actionType = :actionType) AND " +
           "(:result IS NULL OR a.result = :result) AND " +
           "(:startTime IS NULL OR a.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR a.createdAt <= :endTime) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> findByConditions(@Param("username") String username,
                                   @Param("actionType") AuditLog.ActionType actionType,
                                   @Param("result") AuditLog.OperationResult result,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   Pageable pageable);
    
    /**
     * 关键词搜索审计日志
     * 
     * @param keyword 关键词
     * @param pageable 分页参数
     * @return 审计日志分页结果
     */
    @Query("SELECT a FROM AuditLog a WHERE " +
           "a.description LIKE %:keyword% OR " +
           "a.targetType LIKE %:keyword% OR " +
           "a.targetId LIKE %:keyword% OR " +
           "a.errorMessage LIKE %:keyword% " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> findByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    /**
     * 统计指定时间范围内的操作次数
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 操作次数
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.createdAt BETWEEN :startTime AND :endTime")
    long countByCreatedAtBetween(@Param("startTime") LocalDateTime startTime, 
                                @Param("endTime") LocalDateTime endTime);
    
    /**
     * 统计各操作类型的次数
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 操作类型统计
     */
    @Query("SELECT a.actionType, COUNT(a) FROM AuditLog a WHERE " +
           "a.createdAt BETWEEN :startTime AND :endTime GROUP BY a.actionType")
    List<Object[]> countByActionType(@Param("startTime") LocalDateTime startTime, 
                                    @Param("endTime") LocalDateTime endTime);
    
    /**
     * 统计各用户的操作次数
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 用户操作统计
     */
    @Query("SELECT a.username, COUNT(a) FROM AuditLog a WHERE " +
           "a.createdAt BETWEEN :startTime AND :endTime GROUP BY a.username")
    List<Object[]> countByUsername(@Param("startTime") LocalDateTime startTime, 
                                  @Param("endTime") LocalDateTime endTime);
    
    /**
     * 统计失败操作次数
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 失败操作次数
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.result = 'FAILURE' " +
           "AND a.createdAt BETWEEN :startTime AND :endTime")
    long countFailureOperations(@Param("startTime") LocalDateTime startTime, 
                               @Param("endTime") LocalDateTime endTime);
    
    /**
     * 删除指定时间之前的审计日志
     * 
     * @param beforeTime 时间阈值
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :beforeTime")
    int deleteLogsBefore(@Param("beforeTime") LocalDateTime beforeTime);
    
    /**
     * 查询最近的审计日志
     *
     * @return 最近的审计日志
     */
    @Query("SELECT a FROM AuditLog a ORDER BY a.createdAt DESC")
    List<AuditLog> findRecentLogs();
    
    /**
     * 查询指定用户的登录日志
     * 
     * @param username 用户名
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 登录日志列表
     */
    @Query("SELECT a FROM AuditLog a WHERE a.username = :username " +
           "AND a.actionType IN ('LOGIN', 'LOGOUT') " +
           "AND a.createdAt BETWEEN :startTime AND :endTime " +
           "ORDER BY a.createdAt DESC")
    List<AuditLog> findLoginLogsByUser(@Param("username") String username,
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("endTime") LocalDateTime endTime);
}
