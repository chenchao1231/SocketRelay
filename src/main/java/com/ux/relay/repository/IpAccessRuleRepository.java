package com.ux.relay.repository;

import com.ux.relay.entity.IpAccessRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * IP访问控制规则Repository
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Repository
public interface IpAccessRuleRepository extends JpaRepository<IpAccessRule, Long> {
    
    /**
     * 根据转发规则ID查找访问控制规则
     * 
     * @param ruleId 转发规则ID
     * @return 访问控制规则列表
     */
    List<IpAccessRule> findByRuleId(Long ruleId);
    
    /**
     * 根据转发规则ID和启用状态查找访问控制规则
     * 
     * @param ruleId 转发规则ID
     * @param enabled 是否启用
     * @return 访问控制规则列表
     */
    List<IpAccessRule> findByRuleIdAndEnabledOrderByPriorityAsc(Long ruleId, Boolean enabled);
    
    /**
     * 查找全局访问控制规则（ruleId为空）
     * 
     * @param enabled 是否启用
     * @return 全局访问控制规则列表
     */
    @Query("SELECT r FROM IpAccessRule r WHERE r.ruleId IS NULL AND r.enabled = :enabled ORDER BY r.priority ASC")
    List<IpAccessRule> findGlobalRules(@Param("enabled") Boolean enabled);
    
    /**
     * 根据IP地址查找规则
     * 
     * @param ipAddress IP地址
     * @return 访问控制规则列表
     */
    List<IpAccessRule> findByIpAddress(String ipAddress);
    
    /**
     * 根据访问类型查找规则
     * 
     * @param accessType 访问类型
     * @param enabled 是否启用
     * @return 访问控制规则列表
     */
    List<IpAccessRule> findByAccessTypeAndEnabledOrderByPriorityAsc(IpAccessRule.AccessType accessType, Boolean enabled);
    
    /**
     * 查找指定转发规则的启用规则（按优先级排序）
     * 
     * @param ruleId 转发规则ID
     * @return 访问控制规则列表
     */
    @Query("SELECT r FROM IpAccessRule r WHERE (r.ruleId = :ruleId OR r.ruleId IS NULL) AND r.enabled = true ORDER BY r.priority ASC")
    List<IpAccessRule> findEffectiveRulesForRule(@Param("ruleId") Long ruleId);
    
    /**
     * 检查IP地址和转发规则的组合是否已存在
     * 
     * @param ipAddress IP地址
     * @param ruleId 转发规则ID
     * @return 访问控制规则
     */
    Optional<IpAccessRule> findByIpAddressAndRuleId(String ipAddress, Long ruleId);
    
    /**
     * 检查全局IP地址是否已存在
     * 
     * @param ipAddress IP地址
     * @return 访问控制规则
     */
    @Query("SELECT r FROM IpAccessRule r WHERE r.ipAddress = :ipAddress AND r.ruleId IS NULL")
    Optional<IpAccessRule> findGlobalRuleByIpAddress(@Param("ipAddress") String ipAddress);
    
    /**
     * 统计各类型规则数量
     * 
     * @return [访问类型, 数量]
     */
    @Query("SELECT r.accessType, COUNT(r) FROM IpAccessRule r WHERE r.enabled = true GROUP BY r.accessType")
    List<Object[]> countByAccessType();
    
    /**
     * 删除指定转发规则的所有访问控制规则
     * 
     * @param ruleId 转发规则ID
     */
    void deleteByRuleId(Long ruleId);
    
    /**
     * 查找所有启用的规则（按优先级排序）
     * 
     * @return 访问控制规则列表
     */
    List<IpAccessRule> findByEnabledOrderByPriorityAsc(Boolean enabled);
}
