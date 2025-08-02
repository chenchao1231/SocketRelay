package com.ux.relay.service;

import com.ux.relay.entity.IpAccessRule;
import com.ux.relay.repository.IpAccessRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * IP访问控制服务
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Service
public class IpAccessControlService {
    
    private static final Logger logger = LoggerFactory.getLogger(IpAccessControlService.class);
    
    @Autowired
    private IpAccessRuleRepository ipAccessRuleRepository;
    
    /**
     * 检查客户端IP是否允许访问指定转发规则
     * 
     * @param clientIp 客户端IP地址
     * @param ruleId 转发规则ID
     * @return true表示允许访问，false表示拒绝访问
     */
    public boolean isAccessAllowed(String clientIp, Long ruleId) {
        try {
            // 获取生效的访问控制规则（包括全局规则和特定规则）
            List<IpAccessRule> effectiveRules = ipAccessRuleRepository.findEffectiveRulesForRule(ruleId);
            
            if (effectiveRules.isEmpty()) {
                // 如果没有配置任何规则，默认允许访问
                logger.debug("没有配置访问控制规则，默认允许访问: {} -> 规则[{}]", clientIp, ruleId);
                return true;
            }
            
            // 按优先级顺序检查规则
            for (IpAccessRule rule : effectiveRules) {
                if (rule.matches(clientIp)) {
                    boolean allowed = rule.getAccessType() == IpAccessRule.AccessType.ALLOW;
                    
                    logger.info("IP访问控制匹配: {} -> 规则[{}], 匹配规则: {}, 结果: {}", 
                               clientIp, ruleId, rule.getIpAddress(), allowed ? "允许" : "拒绝");
                    
                    return allowed;
                }
            }
            
            // 如果没有匹配的规则，检查是否有白名单规则
            boolean hasAllowRules = effectiveRules.stream()
                .anyMatch(rule -> rule.getAccessType() == IpAccessRule.AccessType.ALLOW);
            
            if (hasAllowRules) {
                // 如果存在白名单规则但没有匹配，则拒绝访问
                logger.info("IP访问控制: {} -> 规则[{}], 存在白名单但未匹配，拒绝访问", clientIp, ruleId);
                return false;
            } else {
                // 如果只有黑名单规则且没有匹配，则允许访问
                logger.debug("IP访问控制: {} -> 规则[{}], 仅有黑名单且未匹配，允许访问", clientIp, ruleId);
                return true;
            }
            
        } catch (Exception e) {
            logger.error("IP访问控制检查异常: {} -> 规则[{}]", clientIp, ruleId, e);
            // 异常情况下默认允许访问，避免影响正常业务
            return true;
        }
    }
    
    /**
     * 创建访问控制规则
     * 
     * @param rule 访问控制规则
     * @param username 创建者用户名
     * @return 创建的规则
     */
    @Transactional
    public IpAccessRule createRule(IpAccessRule rule, String username) {
        // 检查是否已存在相同的规则
        Optional<IpAccessRule> existingRule;
        if (rule.getRuleId() != null) {
            existingRule = ipAccessRuleRepository.findByIpAddressAndRuleId(rule.getIpAddress(), rule.getRuleId());
        } else {
            existingRule = ipAccessRuleRepository.findGlobalRuleByIpAddress(rule.getIpAddress());
        }
        
        if (existingRule.isPresent()) {
            throw new IllegalArgumentException("IP地址 " + rule.getIpAddress() + " 的访问控制规则已存在");
        }
        
        rule.setCreatedBy(username);
        rule.setUpdatedBy(username);
        
        IpAccessRule savedRule = ipAccessRuleRepository.save(rule);
        
        logger.info("创建IP访问控制规则: {} -> 规则[{}], 类型: {}, 创建者: {}", 
                   rule.getIpAddress(), rule.getRuleId(), rule.getAccessType(), username);
        
        return savedRule;
    }
    
    /**
     * 更新访问控制规则
     * 
     * @param rule 访问控制规则
     * @param username 更新者用户名
     * @return 更新的规则
     */
    @Transactional
    public IpAccessRule updateRule(IpAccessRule rule, String username) {
        rule.setUpdatedBy(username);
        
        IpAccessRule updatedRule = ipAccessRuleRepository.save(rule);
        
        logger.info("更新IP访问控制规则: {} -> 规则[{}], 类型: {}, 更新者: {}", 
                   rule.getIpAddress(), rule.getRuleId(), rule.getAccessType(), username);
        
        return updatedRule;
    }
    
    /**
     * 删除访问控制规则
     * 
     * @param id 规则ID
     * @param username 删除者用户名
     */
    @Transactional
    public void deleteRule(Long id, String username) {
        Optional<IpAccessRule> ruleOpt = ipAccessRuleRepository.findById(id);
        if (ruleOpt.isPresent()) {
            IpAccessRule rule = ruleOpt.get();
            ipAccessRuleRepository.deleteById(id);
            
            logger.info("删除IP访问控制规则: {} -> 规则[{}], 类型: {}, 删除者: {}", 
                       rule.getIpAddress(), rule.getRuleId(), rule.getAccessType(), username);
        }
    }
    
    /**
     * 根据ID查找规则
     * 
     * @param id 规则ID
     * @return 访问控制规则
     */
    @Transactional(readOnly = true)
    public Optional<IpAccessRule> findById(Long id) {
        return ipAccessRuleRepository.findById(id);
    }
    
    /**
     * 获取指定转发规则的访问控制规则
     * 
     * @param ruleId 转发规则ID
     * @return 访问控制规则列表
     */
    @Transactional(readOnly = true)
    public List<IpAccessRule> getRulesByForwardRuleId(Long ruleId) {
        return ipAccessRuleRepository.findByRuleId(ruleId);
    }
    
    /**
     * 获取全局访问控制规则
     * 
     * @return 全局访问控制规则列表
     */
    @Transactional(readOnly = true)
    public List<IpAccessRule> getGlobalRules() {
        return ipAccessRuleRepository.findGlobalRules(true);
    }
    
    /**
     * 获取所有访问控制规则
     * 
     * @return 所有访问控制规则列表
     */
    @Transactional(readOnly = true)
    public List<IpAccessRule> getAllRules() {
        return ipAccessRuleRepository.findByEnabledOrderByPriorityAsc(true);
    }
    
    /**
     * 启用/禁用访问控制规则
     * 
     * @param id 规则ID
     * @param enabled 是否启用
     * @param username 操作者用户名
     */
    @Transactional
    public void toggleRule(Long id, boolean enabled, String username) {
        Optional<IpAccessRule> ruleOpt = ipAccessRuleRepository.findById(id);
        if (ruleOpt.isPresent()) {
            IpAccessRule rule = ruleOpt.get();
            rule.setEnabled(enabled);
            rule.setUpdatedBy(username);
            ipAccessRuleRepository.save(rule);
            
            logger.info("{}IP访问控制规则: {} -> 规则[{}], 操作者: {}", 
                       enabled ? "启用" : "禁用", rule.getIpAddress(), rule.getRuleId(), username);
        }
    }
    
    /**
     * 删除指定转发规则的所有访问控制规则
     * 
     * @param ruleId 转发规则ID
     */
    @Transactional
    public void deleteRulesByForwardRuleId(Long ruleId) {
        ipAccessRuleRepository.deleteByRuleId(ruleId);
        logger.info("删除转发规则[{}]的所有IP访问控制规则", ruleId);
    }
    
    /**
     * 获取访问控制统计信息
     * 
     * @return 统计信息
     */
    @Transactional(readOnly = true)
    public AccessControlStats getStats() {
        List<Object[]> stats = ipAccessRuleRepository.countByAccessType();
        
        long allowRules = 0;
        long denyRules = 0;
        
        for (Object[] stat : stats) {
            IpAccessRule.AccessType type = (IpAccessRule.AccessType) stat[0];
            Long count = (Long) stat[1];
            
            if (type == IpAccessRule.AccessType.ALLOW) {
                allowRules = count;
            } else if (type == IpAccessRule.AccessType.DENY) {
                denyRules = count;
            }
        }
        
        return new AccessControlStats(allowRules, denyRules);
    }
    
    /**
     * 访问控制统计信息类
     */
    public static class AccessControlStats {
        private final long allowRules;
        private final long denyRules;
        
        public AccessControlStats(long allowRules, long denyRules) {
            this.allowRules = allowRules;
            this.denyRules = denyRules;
        }
        
        public long getAllowRules() { return allowRules; }
        public long getDenyRules() { return denyRules; }
        public long getTotalRules() { return allowRules + denyRules; }
    }
}
