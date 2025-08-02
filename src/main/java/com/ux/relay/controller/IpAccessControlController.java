package com.ux.relay.controller;

import com.ux.relay.controller.ForwardRuleController.ApiResponse;
import com.ux.relay.entity.IpAccessRule;
import com.ux.relay.service.IpAccessControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

/**
 * IP访问控制API控制器
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@RestController
@RequestMapping("/api/ip-access")
public class IpAccessControlController {
    
    private static final Logger logger = LoggerFactory.getLogger(IpAccessControlController.class);
    
    @Autowired
    private IpAccessControlService ipAccessControlService;
    
    /**
     * 获取所有IP访问控制规则
     */
    @GetMapping
    public ResponseEntity<?> getAllRules() {
        try {
            List<IpAccessRule> rules = ipAccessControlService.getAllRules();
            return ResponseEntity.ok(ApiResponse.success("获取IP访问控制规则成功", rules));
            
        } catch (Exception e) {
            logger.error("获取IP访问控制规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取IP访问控制规则失败"));
        }
    }
    
    /**
     * 根据转发规则ID获取IP访问控制规则
     */
    @GetMapping("/rule/{ruleId}")
    public ResponseEntity<?> getRulesByForwardRuleId(@PathVariable Long ruleId) {
        try {
            List<IpAccessRule> rules = ipAccessControlService.getRulesByForwardRuleId(ruleId);
            return ResponseEntity.ok(ApiResponse.success("获取转发规则IP访问控制规则成功", rules));
            
        } catch (Exception e) {
            logger.error("获取转发规则IP访问控制规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取转发规则IP访问控制规则失败"));
        }
    }
    
    /**
     * 获取全局IP访问控制规则
     */
    @GetMapping("/global")
    public ResponseEntity<?> getGlobalRules() {
        try {
            List<IpAccessRule> rules = ipAccessControlService.getGlobalRules();
            return ResponseEntity.ok(ApiResponse.success("获取全局IP访问控制规则成功", rules));
            
        } catch (Exception e) {
            logger.error("获取全局IP访问控制规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取全局IP访问控制规则失败"));
        }
    }
    
    /**
     * 创建IP访问控制规则
     */
    @PostMapping
    public ResponseEntity<?> createRule(@Valid @RequestBody IpAccessRule rule, Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";
            
            IpAccessRule createdRule = ipAccessControlService.createRule(rule, username);
            return ResponseEntity.ok(ApiResponse.success("创建IP访问控制规则成功", createdRule));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            logger.error("创建IP访问控制规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("创建IP访问控制规则失败"));
        }
    }
    
    /**
     * 更新IP访问控制规则
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRule(@PathVariable Long id, 
                                       @Valid @RequestBody IpAccessRule rule, 
                                       Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";
            
            Optional<IpAccessRule> existingRuleOpt = ipAccessControlService.findById(id);
            if (!existingRuleOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            rule.setId(id);
            IpAccessRule updatedRule = ipAccessControlService.updateRule(rule, username);
            return ResponseEntity.ok(ApiResponse.success("更新IP访问控制规则成功", updatedRule));
            
        } catch (Exception e) {
            logger.error("更新IP访问控制规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("更新IP访问控制规则失败"));
        }
    }
    
    /**
     * 删除IP访问控制规则
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable Long id, Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";
            
            Optional<IpAccessRule> ruleOpt = ipAccessControlService.findById(id);
            if (!ruleOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            ipAccessControlService.deleteRule(id, username);
            return ResponseEntity.ok(ApiResponse.success("删除IP访问控制规则成功"));
            
        } catch (Exception e) {
            logger.error("删除IP访问控制规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("删除IP访问控制规则失败"));
        }
    }
    
    /**
     * 启用/禁用IP访问控制规则
     */
    @PostMapping("/{id}/toggle")
    public ResponseEntity<?> toggleRule(@PathVariable Long id, 
                                       @RequestParam boolean enabled, 
                                       Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";
            
            Optional<IpAccessRule> ruleOpt = ipAccessControlService.findById(id);
            if (!ruleOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }
            
            ipAccessControlService.toggleRule(id, enabled, username);
            return ResponseEntity.ok(ApiResponse.success(enabled ? "启用IP访问控制规则成功" : "禁用IP访问控制规则成功"));
            
        } catch (Exception e) {
            logger.error("切换IP访问控制规则状态异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("切换IP访问控制规则状态失败"));
        }
    }
    
    /**
     * 测试IP访问权限
     */
    @PostMapping("/test")
    public ResponseEntity<?> testAccess(@RequestParam String clientIp, 
                                       @RequestParam(required = false) Long ruleId) {
        try {
            boolean allowed = ipAccessControlService.isAccessAllowed(clientIp, ruleId);
            
            TestResult result = new TestResult(clientIp, ruleId, allowed);
            return ResponseEntity.ok(ApiResponse.success("IP访问权限测试完成", result));
            
        } catch (Exception e) {
            logger.error("IP访问权限测试异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("IP访问权限测试失败"));
        }
    }
    
    /**
     * 获取IP访问控制统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        try {
            IpAccessControlService.AccessControlStats stats = ipAccessControlService.getStats();
            return ResponseEntity.ok(ApiResponse.success("获取IP访问控制统计信息成功", stats));
            
        } catch (Exception e) {
            logger.error("获取IP访问控制统计信息异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取IP访问控制统计信息失败"));
        }
    }
    
    /**
     * 测试结果类
     */
    public static class TestResult {
        private String clientIp;
        private Long ruleId;
        private boolean allowed;
        private String message;
        
        public TestResult(String clientIp, Long ruleId, boolean allowed) {
            this.clientIp = clientIp;
            this.ruleId = ruleId;
            this.allowed = allowed;
            this.message = allowed ? "访问被允许" : "访问被拒绝";
        }
        
        // Getters and Setters
        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }
        
        public Long getRuleId() { return ruleId; }
        public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
        
        public boolean isAllowed() { return allowed; }
        public void setAllowed(boolean allowed) { this.allowed = allowed; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}
