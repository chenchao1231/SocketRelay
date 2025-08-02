package com.ux.relay.repository;

import com.ux.relay.entity.ForwardRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 转发规则数据访问接口
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Repository
public interface ForwardRuleRepository extends JpaRepository<ForwardRule, Long> {
    
    /**
     * 根据启用状态查询转发规则
     * 
     * @param enabled 是否启用
     * @return 转发规则列表
     */
    List<ForwardRule> findByEnabled(Boolean enabled);
    
    /**
     * 根据协议类型查询转发规则
     * 
     * @param protocol 协议类型
     * @return 转发规则列表
     */
    List<ForwardRule> findByProtocol(ForwardRule.ProtocolType protocol);
    
    /**
     * 根据源端口查询转发规则
     * 
     * @param sourcePort 源端口
     * @return 转发规则列表
     */
    List<ForwardRule> findBySourcePort(Integer sourcePort);
    
    /**
     * 根据源端口和协议查询转发规则
     * 
     * @param sourcePort 源端口
     * @param protocol 协议类型
     * @return 转发规则
     */
    Optional<ForwardRule> findBySourcePortAndProtocol(Integer sourcePort, ForwardRule.ProtocolType protocol);
    
    /**
     * 根据规则名称查询转发规则
     * 
     * @param ruleName 规则名称
     * @return 转发规则
     */
    Optional<ForwardRule> findByRuleName(String ruleName);
    
    /**
     * 检查端口是否已被使用
     * 
     * @param sourcePort 源端口
     * @param protocol 协议类型
     * @param excludeId 排除的规则ID（用于更新时检查）
     * @return 是否存在冲突
     */
    @Query("SELECT COUNT(r) > 0 FROM ForwardRule r WHERE r.sourcePort = :sourcePort " +
           "AND (r.protocol = :protocol OR r.protocol = 'TCP_UDP' OR :protocol = 'TCP_UDP') " +
           "AND r.enabled = true AND (:excludeId IS NULL OR r.id != :excludeId)")
    boolean existsPortConflict(@Param("sourcePort") Integer sourcePort, 
                              @Param("protocol") ForwardRule.ProtocolType protocol,
                              @Param("excludeId") Long excludeId);
    
    /**
     * 查询启用的转发规则，按创建时间排序
     * 
     * @return 启用的转发规则列表
     */
    @Query("SELECT r FROM ForwardRule r WHERE r.enabled = true ORDER BY r.createdAt ASC")
    List<ForwardRule> findEnabledRulesOrderByCreatedAt();
    
    /**
     * 根据目标地址查询转发规则
     * 
     * @param targetIp 目标IP
     * @param targetPort 目标端口
     * @return 转发规则列表
     */
    List<ForwardRule> findByTargetIpAndTargetPort(String targetIp, Integer targetPort);
    
    /**
     * 模糊查询转发规则
     * 
     * @param keyword 关键词
     * @return 转发规则列表
     */
    @Query("SELECT r FROM ForwardRule r WHERE " +
           "r.ruleName LIKE %:keyword% OR " +
           "r.sourceIp LIKE %:keyword% OR " +
           "r.targetIp LIKE %:keyword% OR " +
           "r.remark LIKE %:keyword%")
    List<ForwardRule> findByKeyword(@Param("keyword") String keyword);
    
    /**
     * 统计启用的转发规则数量
     * 
     * @return 启用的转发规则数量
     */
    @Query("SELECT COUNT(r) FROM ForwardRule r WHERE r.enabled = true")
    long countEnabledRules();
    
    /**
     * 统计各协议类型的规则数量
     * 
     * @return 协议类型统计
     */
    @Query("SELECT r.protocol, COUNT(r) FROM ForwardRule r WHERE r.enabled = true GROUP BY r.protocol")
    List<Object[]> countByProtocol();
}
