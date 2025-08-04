package com.ux.relay.service;

import com.ux.relay.entity.AuditLog;
import com.ux.relay.entity.ForwardRule;
import com.ux.relay.repository.ForwardRuleRepository;
import com.ux.relay.core.ForwardingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

/**
 * 转发规则服务
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Service
@Transactional
public class ForwardRuleService {
    
    private static final Logger logger = LoggerFactory.getLogger(ForwardRuleService.class);
    
    @Autowired
    private ForwardRuleRepository forwardRuleRepository;
    
    @Autowired
    private ForwardingEngine forwardingEngine;
    
    @Autowired
    private AuditService auditService;
    
    @PostConstruct
    public void init() {
        // 系统启动时自动启动所有启用的转发规则
        startAllEnabledRules();
    }
    
    /**
     * 创建转发规则
     * 
     * @param rule 转发规则
     * @param username 操作用户
     * @return 创建的转发规则
     */
    public ForwardRule createRule(ForwardRule rule, String username) {
        logger.info("创建转发规则: {}", rule.getRuleName());
        
        // 检查端口冲突
        if (checkPortConflict(rule.getSourcePort(), rule.getProtocol(), null)) {
            throw new IllegalArgumentException("端口冲突: " + rule.getSourcePort() + " (" + rule.getProtocol() + ")");
        }
        
        // 检查规则名称重复
        if (forwardRuleRepository.findByRuleName(rule.getRuleName()).isPresent()) {
            throw new IllegalArgumentException("规则名称已存在: " + rule.getRuleName());
        }
        
        // 设置创建者
        rule.setCreatedBy(username);
        rule.setUpdatedBy(username);
        
        // 保存规则
        ForwardRule savedRule = forwardRuleRepository.save(rule);
        
        // 如果规则启用，立即启动转发
        if (savedRule.getEnabled()) {
            boolean success = forwardingEngine.startForwarding(savedRule);
            if (!success) {
                logger.warn("转发规则启动失败: {}", savedRule.getRuleName());
            }
        }
        
        // 记录审计日志
        auditService.logAction(AuditLog.ActionType.CREATE_RULE, username, 
                              "创建转发规则: " + rule.getRuleName(), 
                              "ForwardRule", savedRule.getId().toString());
        
        logger.info("转发规则创建成功: {}", savedRule.getRuleName());
        return savedRule;
    }

    /**
     * 保存转发规则（不启动转发）
     *
     * @param rule 转发规则
     * @return 保存后的规则
     */
    @Transactional
    public ForwardRule saveRule(ForwardRule rule) {
        return forwardRuleRepository.save(rule);
    }

    /**
     * 根据源端口和协议查找规则
     *
     * @param sourcePort 源端口
     * @param protocol 协议
     * @return 转发规则
     */
    @Transactional(readOnly = true)
    public Optional<ForwardRule> findBySourcePortAndProtocol(Integer sourcePort, ForwardRule.ProtocolType protocol) {
        return forwardRuleRepository.findBySourcePortAndProtocol(sourcePort, protocol);
    }

    /**
     * 更新转发规则
     * 
     * @param id 规则ID
     * @param rule 更新的规则信息
     * @param username 操作用户
     * @return 更新后的转发规则
     */
    public ForwardRule updateRule(Long id, ForwardRule rule, String username) {
        logger.info("更新转发规则: {}", id);
        
        Optional<ForwardRule> existingRuleOpt = forwardRuleRepository.findById(id);
        if (!existingRuleOpt.isPresent()) {
            throw new IllegalArgumentException("转发规则不存在: " + id);
        }
        
        ForwardRule existingRule = existingRuleOpt.get();
        
        // 检查端口冲突（排除当前规则）
        if (checkPortConflict(rule.getSourcePort(), rule.getProtocol(), id)) {
            throw new IllegalArgumentException("端口冲突: " + rule.getSourcePort() + " (" + rule.getProtocol() + ")");
        }
        
        // 检查规则名称重复（排除当前规则）
        Optional<ForwardRule> duplicateRule = forwardRuleRepository.findByRuleName(rule.getRuleName());
        if (duplicateRule.isPresent() && !duplicateRule.get().getId().equals(id)) {
            throw new IllegalArgumentException("规则名称已存在: " + rule.getRuleName());
        }
        
        // 如果规则正在运行且关键参数发生变化，需要重启转发
        boolean needRestart = existingRule.getEnabled() && 
                             (!existingRule.getSourcePort().equals(rule.getSourcePort()) ||
                              !existingRule.getTargetIp().equals(rule.getTargetIp()) ||
                              !existingRule.getTargetPort().equals(rule.getTargetPort()) ||
                              !existingRule.getProtocol().equals(rule.getProtocol()));
        
        if (needRestart) {
            forwardingEngine.stopForwarding(existingRule);
        }
        
        // 更新规则信息
        existingRule.setRuleName(rule.getRuleName());
        existingRule.setSourceIp(rule.getSourceIp());
        existingRule.setSourcePort(rule.getSourcePort());
        existingRule.setTargetIp(rule.getTargetIp());
        existingRule.setTargetPort(rule.getTargetPort());
        existingRule.setProtocol(rule.getProtocol());
        existingRule.setEnabled(rule.getEnabled());
        existingRule.setRemark(rule.getRemark());
        existingRule.setUpdatedBy(username);
        
        // 保存更新
        ForwardRule updatedRule = forwardRuleRepository.save(existingRule);
        
        // 如果规则启用，启动转发
        if (updatedRule.getEnabled()) {
            boolean success = forwardingEngine.startForwarding(updatedRule);
            if (!success) {
                logger.warn("转发规则启动失败: {}", updatedRule.getRuleName());
            }
        }
        
        // 记录审计日志
        auditService.logAction(AuditLog.ActionType.UPDATE_RULE, username, 
                              "更新转发规则: " + rule.getRuleName(), 
                              "ForwardRule", updatedRule.getId().toString());
        
        logger.info("转发规则更新成功: {}", updatedRule.getRuleName());
        return updatedRule;
    }
    
    /**
     * 删除转发规则
     * 
     * @param id 规则ID
     * @param username 操作用户
     */
    public void deleteRule(Long id, String username) {
        logger.info("删除转发规则: {}", id);
        
        Optional<ForwardRule> ruleOpt = forwardRuleRepository.findById(id);
        if (!ruleOpt.isPresent()) {
            throw new IllegalArgumentException("转发规则不存在: " + id);
        }
        
        ForwardRule rule = ruleOpt.get();
        
        // 停止转发
        if (rule.getEnabled()) {
            forwardingEngine.stopForwarding(rule);
        }
        
        // 删除规则
        forwardRuleRepository.delete(rule);
        
        // 记录审计日志
        auditService.logAction(AuditLog.ActionType.DELETE_RULE, username, 
                              "删除转发规则: " + rule.getRuleName(), 
                              "ForwardRule", rule.getId().toString());
        
        logger.info("转发规则删除成功: {}", rule.getRuleName());
    }
    
    /**
     * 启用转发规则
     * 
     * @param id 规则ID
     * @param username 操作用户
     * @return 更新后的转发规则
     */
    public ForwardRule enableRule(Long id, String username) {
        logger.info("启用转发规则: {}", id);
        
        Optional<ForwardRule> ruleOpt = forwardRuleRepository.findById(id);
        if (!ruleOpt.isPresent()) {
            throw new IllegalArgumentException("转发规则不存在: " + id);
        }
        
        ForwardRule rule = ruleOpt.get();
        
        if (!rule.getEnabled()) {
            rule.setEnabled(true);
            rule.setUpdatedBy(username);
            rule = forwardRuleRepository.save(rule);
            
            // 启动转发
            boolean success = forwardingEngine.startForwarding(rule);
            if (!success) {
                throw new RuntimeException("转发规则启动失败: " + rule.getRuleName());
            }
            
            // 记录审计日志
            auditService.logAction(AuditLog.ActionType.ENABLE_RULE, username, 
                                  "启用转发规则: " + rule.getRuleName(), 
                                  "ForwardRule", rule.getId().toString());
        }
        
        logger.info("转发规则启用成功: {}", rule.getRuleName());
        return rule;
    }
    
    /**
     * 禁用转发规则
     * 
     * @param id 规则ID
     * @param username 操作用户
     * @return 更新后的转发规则
     */
    public ForwardRule disableRule(Long id, String username) {
        logger.info("禁用转发规则: {}", id);
        
        Optional<ForwardRule> ruleOpt = forwardRuleRepository.findById(id);
        if (!ruleOpt.isPresent()) {
            throw new IllegalArgumentException("转发规则不存在: " + id);
        }
        
        ForwardRule rule = ruleOpt.get();
        
        if (rule.getEnabled()) {
            rule.setEnabled(false);
            rule.setUpdatedBy(username);
            rule = forwardRuleRepository.save(rule);
            
            // 停止转发
            forwardingEngine.stopForwarding(rule);
            
            // 记录审计日志
            auditService.logAction(AuditLog.ActionType.DISABLE_RULE, username, 
                                  "禁用转发规则: " + rule.getRuleName(), 
                                  "ForwardRule", rule.getId().toString());
        }
        
        logger.info("转发规则禁用成功: {}", rule.getRuleName());
        return rule;
    }
    
    /**
     * 根据ID查询转发规则
     * 
     * @param id 规则ID
     * @return 转发规则
     */
    @Transactional(readOnly = true)
    public Optional<ForwardRule> findById(Long id) {
        return forwardRuleRepository.findById(id);
    }
    
    /**
     * 查询所有转发规则
     * 
     * @param pageable 分页参数
     * @return 转发规则分页结果
     */
    @Transactional(readOnly = true)
    public Page<ForwardRule> findAll(Pageable pageable) {
        return forwardRuleRepository.findAll(pageable);
    }
    
    /**
     * 根据启用状态查询转发规则
     * 
     * @param enabled 是否启用
     * @return 转发规则列表
     */
    @Transactional(readOnly = true)
    public List<ForwardRule> findByEnabled(Boolean enabled) {
        return forwardRuleRepository.findByEnabled(enabled);
    }
    
    /**
     * 关键词搜索转发规则
     * 
     * @param keyword 关键词
     * @return 转发规则列表
     */
    @Transactional(readOnly = true)
    public List<ForwardRule> searchByKeyword(String keyword) {
        return forwardRuleRepository.findByKeyword(keyword);
    }
    
    /**
     * 检查端口冲突
     * 
     * @param sourcePort 源端口
     * @param protocol 协议类型
     * @param excludeId 排除的规则ID
     * @return 是否存在冲突
     */
    private boolean checkPortConflict(Integer sourcePort, ForwardRule.ProtocolType protocol, Long excludeId) {
        return forwardRuleRepository.existsPortConflict(sourcePort, protocol, excludeId);
    }
    
    /**
     * 启动所有启用的转发规则
     */
    private void startAllEnabledRules() {
        logger.info("启动所有启用的转发规则...");
        
        List<ForwardRule> enabledRules = forwardRuleRepository.findEnabledRulesOrderByCreatedAt();
        int successCount = 0;
        
        for (ForwardRule rule : enabledRules) {
            try {
                // 验证规则数据完整性
                if (!validateRule(rule)) {
                    logger.error("规则数据不完整，跳过启动: {}", rule.getRuleName());
                    continue;
                }

                boolean success = forwardingEngine.startForwarding(rule);
                if (success) {
                    successCount++;
                    logger.info("转发规则启动成功: {}", rule.getRuleName());
                } else {
                    logger.warn("转发规则启动失败: {}", rule.getRuleName());
                }
            } catch (Exception e) {
                logger.error("转发规则启动异常: {}", rule.getRuleName(), e);
            }
        }
        
        logger.info("转发规则启动完成: {}/{}", successCount, enabledRules.size());
    }

    /**
     * 验证规则数据完整性
     */
    private boolean validateRule(ForwardRule rule) {
        if (rule == null) {
            logger.error("规则对象为null");
            return false;
        }

        if (rule.getRuleName() == null || rule.getRuleName().trim().isEmpty()) {
            logger.error("规则名称为空: {}", rule.getId());
            return false;
        }

        if (rule.getProtocol() == null) {
            logger.error("规则协议为null: {}", rule.getRuleName());
            return false;
        }

        if (rule.getSourcePort() == null) {
            logger.error("规则源端口为null: {}", rule.getRuleName());
            return false;
        }

        if (rule.getTargetPort() == null) {
            logger.error("规则目标端口为null: {}", rule.getRuleName());
            return false;
        }

        if (rule.getTargetIp() == null || rule.getTargetIp().trim().isEmpty()) {
            logger.error("规则目标IP为空: {}", rule.getRuleName());
            return false;
        }

        // 端口范围检查
        if (rule.getSourcePort() < 1 || rule.getSourcePort() > 65535) {
            logger.error("规则源端口超出范围: {} -> {}", rule.getRuleName(), rule.getSourcePort());
            return false;
        }

        if (rule.getTargetPort() < 1 || rule.getTargetPort() > 65535) {
            logger.error("规则目标端口超出范围: {} -> {}", rule.getRuleName(), rule.getTargetPort());
            return false;
        }

        logger.debug("规则验证通过: {} ({}:{} -> {}:{})",
                    rule.getRuleName(), rule.getSourceIp(), rule.getSourcePort(),
                    rule.getTargetIp(), rule.getTargetPort());
        return true;
    }
    
    /**
     * 获取转发规则统计信息
     * 
     * @return 统计信息
     */
    @Transactional(readOnly = true)
    public RuleStatistics getStatistics() {
        long totalRules = forwardRuleRepository.count();
        long enabledRules = forwardRuleRepository.countEnabledRules();
        List<Object[]> protocolStats = forwardRuleRepository.countByProtocol();
        
        RuleStatistics statistics = new RuleStatistics();
        statistics.setTotalRules(totalRules);
        statistics.setEnabledRules(enabledRules);
        statistics.setDisabledRules(totalRules - enabledRules);
        
        for (Object[] stat : protocolStats) {
            ForwardRule.ProtocolType protocol = (ForwardRule.ProtocolType) stat[0];
            Long count = (Long) stat[1];
            
            switch (protocol) {
                case TCP:
                    statistics.setTcpRules(count);
                    break;
                case UDP:
                    statistics.setUdpRules(count);
                    break;
                case TCP_UDP:
                    statistics.setTcpUdpRules(count);
                    break;
            }
        }
        
        return statistics;
    }
    
    /**
     * 规则统计信息类
     */
    public static class RuleStatistics {
        private long totalRules;
        private long enabledRules;
        private long disabledRules;
        private long tcpRules;
        private long udpRules;
        private long tcpUdpRules;
        
        // Getters and Setters
        public long getTotalRules() { return totalRules; }
        public void setTotalRules(long totalRules) { this.totalRules = totalRules; }
        
        public long getEnabledRules() { return enabledRules; }
        public void setEnabledRules(long enabledRules) { this.enabledRules = enabledRules; }
        
        public long getDisabledRules() { return disabledRules; }
        public void setDisabledRules(long disabledRules) { this.disabledRules = disabledRules; }
        
        public long getTcpRules() { return tcpRules; }
        public void setTcpRules(long tcpRules) { this.tcpRules = tcpRules; }
        
        public long getUdpRules() { return udpRules; }
        public void setUdpRules(long udpRules) { this.udpRules = udpRules; }
        
        public long getTcpUdpRules() { return tcpUdpRules; }
        public void setTcpUdpRules(long tcpUdpRules) { this.tcpUdpRules = tcpUdpRules; }
    }
}
