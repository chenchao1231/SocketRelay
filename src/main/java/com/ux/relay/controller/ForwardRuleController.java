package com.ux.relay.controller;

import com.ux.relay.entity.ForwardRule;
import com.ux.relay.service.ForwardRuleService;
import com.ux.relay.core.ConnectionPoolManager;
import com.ux.relay.core.ClientConnectionManager;
import com.ux.relay.core.UdpBroadcastManager;
import com.ux.relay.core.UdpBroadcastForwardingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 转发规则控制器
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@RestController
@RequestMapping("/api/rules")
@CrossOrigin(origins = "*")
public class ForwardRuleController {
    
    private static final Logger logger = LoggerFactory.getLogger(ForwardRuleController.class);
    
    @Autowired
    private ForwardRuleService forwardRuleService;

    @Autowired
    private ConnectionPoolManager connectionPoolManager;

    @Autowired
    private ClientConnectionManager clientConnectionManager;

    @Autowired
    private UdpBroadcastManager udpBroadcastManager;

    @Autowired
    private com.ux.relay.service.ClientListenerStatusService clientListenerStatusService;

    @Autowired
    private com.ux.relay.service.ConnectionService connectionService;
    
    /**
     * 创建转发规则
     */
    @PostMapping
    public ResponseEntity<?> createRule(@Valid @RequestBody ForwardRule rule, Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";
            ForwardRule createdRule = forwardRuleService.createRule(rule, username);
            
            return ResponseEntity.ok(ApiResponse.success("转发规则创建成功", createdRule));
            
        } catch (IllegalArgumentException e) {
            logger.warn("创建转发规则失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
            
        } catch (Exception e) {
            logger.error("创建转发规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("创建转发规则失败"));
        }
    }
    
    /**
     * 更新转发规则
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRule(@PathVariable Long id, 
                                       @Valid @RequestBody ForwardRule rule, 
                                       Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";
            ForwardRule updatedRule = forwardRuleService.updateRule(id, rule, username);
            
            return ResponseEntity.ok(ApiResponse.success("转发规则更新成功", updatedRule));
            
        } catch (IllegalArgumentException e) {
            logger.warn("更新转发规则失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
            
        } catch (Exception e) {
            logger.error("更新转发规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("更新转发规则失败"));
        }
    }
    
    /**
     * 删除转发规则
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRule(@PathVariable Long id, Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";
            forwardRuleService.deleteRule(id, username);
            
            return ResponseEntity.ok(ApiResponse.success("转发规则删除成功"));
            
        } catch (IllegalArgumentException e) {
            logger.warn("删除转发规则失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
            
        } catch (Exception e) {
            logger.error("删除转发规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("删除转发规则失败"));
        }
    }
    
    /**
     * 启用转发规则
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<?> enableRule(@PathVariable Long id, Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";
            ForwardRule rule = forwardRuleService.enableRule(id, username);
            
            return ResponseEntity.ok(ApiResponse.success("转发规则启用成功", rule));
            
        } catch (IllegalArgumentException e) {
            logger.warn("启用转发规则失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
            
        } catch (Exception e) {
            logger.error("启用转发规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("启用转发规则失败"));
        }
    }
    
    /**
     * 禁用转发规则
     */
    @PostMapping("/{id}/disable")
    public ResponseEntity<?> disableRule(@PathVariable Long id, Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";
            ForwardRule rule = forwardRuleService.disableRule(id, username);
            
            return ResponseEntity.ok(ApiResponse.success("转发规则禁用成功", rule));
            
        } catch (IllegalArgumentException e) {
            logger.warn("禁用转发规则失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
            
        } catch (Exception e) {
            logger.error("禁用转发规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("禁用转发规则失败"));
        }
    }
    
    /**
     * 根据ID查询转发规则
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getRule(@PathVariable Long id) {
        try {
            Optional<ForwardRule> rule = forwardRuleService.findById(id);
            
            if (rule.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success("查询成功", rule.get()));
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            logger.error("查询转发规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("查询转发规则失败"));
        }
    }
    
    /**
     * 分页查询转发规则
     */
    @GetMapping
    public ResponseEntity<?> getRules(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(defaultValue = "id") String sort,
                                     @RequestParam(defaultValue = "desc") String direction) {
        try {
            Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? 
                                         Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));
            
            Page<ForwardRule> rules = forwardRuleService.findAll(pageable);
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", rules));
            
        } catch (Exception e) {
            logger.error("分页查询转发规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("查询转发规则失败"));
        }
    }
    
    /**
     * 根据启用状态查询转发规则
     */
    @GetMapping("/by-status")
    public ResponseEntity<?> getRulesByStatus(@RequestParam Boolean enabled) {
        try {
            List<ForwardRule> rules = forwardRuleService.findByEnabled(enabled);
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", rules));
            
        } catch (Exception e) {
            logger.error("根据状态查询转发规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("查询转发规则失败"));
        }
    }
    
    /**
     * 搜索转发规则
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchRules(@RequestParam String keyword) {
        try {
            List<ForwardRule> rules = forwardRuleService.searchByKeyword(keyword);
            
            return ResponseEntity.ok(ApiResponse.success("搜索成功", rules));
            
        } catch (Exception e) {
            logger.error("搜索转发规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("搜索转发规则失败"));
        }
    }
    
    /**
     * 获取转发规则统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics() {
        try {
            ForwardRuleService.RuleStatistics statistics = forwardRuleService.getStatistics();
            
            return ResponseEntity.ok(ApiResponse.success("统计信息获取成功", statistics));
            
        } catch (Exception e) {
            logger.error("获取转发规则统计信息异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取统计信息失败"));
        }
    }

    /**
     * 获取转发规则详细状态
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<?> getRuleStatus(@PathVariable Long id) {
        try {
            Optional<ForwardRule> ruleOpt = forwardRuleService.findById(id);
            if (!ruleOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            ForwardRule rule = ruleOpt.get();

            // 获取连接池状态
            ConnectionPoolManager.ConnectionPoolStatus poolStatus =
                connectionPoolManager.getPoolStatus(id);

            // 获取客户端连接统计
            ClientConnectionManager.ClientConnectionStats clientStats =
                clientConnectionManager.getClientConnectionStats(id);

            RuleDetailedStatus status = new RuleDetailedStatus(
                rule,
                poolStatus,
                clientStats
            );

            return ResponseEntity.ok(ApiResponse.success("规则状态获取成功", status));

        } catch (Exception e) {
            logger.error("获取转发规则状态异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取规则状态失败"));
        }
    }

    /**
     * 获取所有规则的状态概览
     */
    @GetMapping("/status-overview")
    public ResponseEntity<?> getRulesStatusOverview() {
        try {
            List<ForwardRule> rules = forwardRuleService.findByEnabled(true);
            List<RuleStatusOverview> statusList = new ArrayList<>();

            for (ForwardRule rule : rules) {
                ConnectionPoolManager.ConnectionPoolStatus poolStatus =
                    connectionPoolManager.getPoolStatus(rule.getId());

                int clientCount;

                // 对于UDP协议，尝试从UDP广播管理器获取实时客户端数量
                if (rule.getProtocol() == ForwardRule.ProtocolType.UDP) {
                    try {
                        UdpBroadcastForwardingHandler handler = udpBroadcastManager.getHandler(rule.getId());
                        if (handler != null) {
                            UdpBroadcastForwardingHandler.ClientStatistics stats = handler.getClientStatistics();
                            clientCount = stats.getTotalClientCount();
                        } else {
                            // 如果UDP广播处理器不存在，使用传统方法
                            clientCount = clientConnectionManager.getClientConnectionCount(rule.getId());
                        }
                    } catch (Exception e) {
                        logger.warn("获取UDP广播客户端统计失败，使用传统方法: ruleId={}", rule.getId(), e);
                        clientCount = clientConnectionManager.getClientConnectionCount(rule.getId());
                    }
                } else {
                    // 对于TCP协议，使用传统方法
                    clientCount = clientConnectionManager.getClientConnectionCount(rule.getId());
                }

                RuleStatusOverview overview = new RuleStatusOverview(
                    rule.getId(),
                    rule.getRuleName(),
                    rule.getDataSourceName() != null ? rule.getDataSourceName() :
                        (rule.getTargetIp() + ":" + rule.getTargetPort()),
                    poolStatus != null ? poolStatus.getStatus() : "UNKNOWN",
                    poolStatus != null ? poolStatus.getActiveConnections() : 0,
                    poolStatus != null ? poolStatus.getTotalConnections() : 0,
                    clientCount,
                    poolStatus != null ? poolStatus.getReconnectionAttempts() : 0
                );

                statusList.add(overview);
            }

            return ResponseEntity.ok(ApiResponse.success("规则状态概览获取成功", statusList));

        } catch (Exception e) {
            logger.error("获取规则状态概览异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取规则状态概览失败"));
        }
    }

    /**
     * 编辑转发规则（基础配置，不重启转发）
     */
    @PutMapping("/{id}/edit")
    public ResponseEntity<?> editRule(@PathVariable Long id,
                                     @Valid @RequestBody ForwardRule rule,
                                     Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";

            // 检查规则是否存在
            Optional<ForwardRule> existingRuleOpt = forwardRuleService.findById(id);
            if (!existingRuleOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            ForwardRule existingRule = existingRuleOpt.get();

            // 只允许编辑某些字段，不允许修改核心转发配置
            existingRule.setRuleName(rule.getRuleName());
            existingRule.setDataSourceName(rule.getDataSourceName());
            existingRule.setRemark(rule.getRemark());
            existingRule.setAutoReconnect(rule.getAutoReconnect());
            existingRule.setReconnectInterval(rule.getReconnectInterval());
            existingRule.setMaxReconnectAttempts(rule.getMaxReconnectAttempts());
            existingRule.setConnectionPoolSize(rule.getConnectionPoolSize());
            existingRule.setUpdatedBy(username);
            existingRule.setUpdatedAt(java.time.LocalDateTime.now());

            ForwardRule updatedRule = forwardRuleService.saveRule(existingRule);

            return ResponseEntity.ok(ApiResponse.success("转发规则编辑成功", updatedRule));

        } catch (Exception e) {
            logger.error("编辑转发规则异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("编辑转发规则失败"));
        }
    }

    /**
     * 编辑转发规则核心配置（需要先停止规则）
     */
    @PutMapping("/{id}/edit-core")
    public ResponseEntity<?> editCoreConfig(@PathVariable Long id,
                                           @Valid @RequestBody ForwardRule rule,
                                           Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";

            // 检查规则是否存在
            Optional<ForwardRule> existingRuleOpt = forwardRuleService.findById(id);
            if (!existingRuleOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            ForwardRule existingRule = existingRuleOpt.get();

            // 检查规则是否已停止
            if (existingRule.getEnabled()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("请先停止规则后再修改核心配置"));
            }

            // 检查端口是否被其他规则占用
            if (!existingRule.getSourcePort().equals(rule.getSourcePort())) {
                Optional<ForwardRule> conflictRule = forwardRuleService.findBySourcePortAndProtocol(
                    rule.getSourcePort(), rule.getProtocol());
                if (conflictRule.isPresent() && !conflictRule.get().getId().equals(id)) {
                    return ResponseEntity.badRequest()
                        .body(ApiResponse.error("端口 " + rule.getSourcePort() + " 已被其他规则使用"));
                }
            }

            // 更新核心配置
            existingRule.setRuleName(rule.getRuleName());
            existingRule.setProtocol(rule.getProtocol());
            existingRule.setSourcePort(rule.getSourcePort());
            existingRule.setTargetIp(rule.getTargetIp());
            existingRule.setTargetPort(rule.getTargetPort());
            existingRule.setDataSourceName(rule.getDataSourceName());
            existingRule.setRemark(rule.getRemark());
            existingRule.setAutoReconnect(rule.getAutoReconnect());
            existingRule.setReconnectInterval(rule.getReconnectInterval());
            existingRule.setMaxReconnectAttempts(rule.getMaxReconnectAttempts());
            existingRule.setConnectionPoolSize(rule.getConnectionPoolSize());
            existingRule.setUpdatedBy(username);
            existingRule.setUpdatedAt(java.time.LocalDateTime.now());

            ForwardRule updatedRule = forwardRuleService.saveRule(existingRule);

            return ResponseEntity.ok(ApiResponse.success("转发规则核心配置编辑成功", updatedRule));

        } catch (Exception e) {
            logger.error("编辑转发规则核心配置异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("编辑转发规则核心配置失败"));
        }
    }

    /**
     * 获取规则的客户端连接详情
     */
    @GetMapping("/{id}/client-connections")
    public ResponseEntity<?> getRuleClientConnections(@PathVariable Long id) {
        try {
            Optional<ForwardRule> ruleOpt = forwardRuleService.findById(id);
            if (!ruleOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            ForwardRule rule = ruleOpt.get();

            // 获取客户端连接统计
            ClientConnectionManager.ClientConnectionStats clientStats =
                clientConnectionManager.getClientConnectionStats(id);

            // 获取连接池状态
            ConnectionPoolManager.ConnectionPoolStatus poolStatus =
                connectionPoolManager.getPoolStatus(id);

            // 获取客户端监听状态
            List<com.ux.relay.entity.ClientListenerStatus> listenerStatuses =
                clientListenerStatusService.getListenerStatus(id);

            // 获取活跃连接详情（从ConnectionService获取）
            List<com.ux.relay.entity.ConnectionInfo> activeConnections =
                connectionService.findActiveConnectionsByRuleId(id);

            RuleClientConnectionDetails details = new RuleClientConnectionDetails(
                rule,
                clientStats,
                poolStatus,
                listenerStatuses,
                activeConnections
            );

            return ResponseEntity.ok(ApiResponse.success("客户端连接详情获取成功", details));

        } catch (Exception e) {
            logger.error("获取规则客户端连接详情异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取客户端连接详情失败"));
        }
    }

    /**
     * 清空所有连接记录（危险操作）
     */
    @DeleteMapping("/connections/clear")
    public ResponseEntity<?> clearAllConnections(Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";

            connectionService.clearAllConnections();

            logger.info("清空所有连接记录，操作者: {}", username);
            return ResponseEntity.ok(ApiResponse.success("清空所有连接记录成功"));

        } catch (Exception e) {
            logger.error("清空连接记录异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("清空连接记录失败"));
        }
    }

    /**
     * 清除历史连接记录（只删除非活跃连接）
     */
    @DeleteMapping("/connections/clear-history")
    public ResponseEntity<?> clearHistoryConnections(Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";

            int clearedCount = connectionService.clearHistoryConnections();

            logger.info("清除历史连接记录，删除数量: {}, 操作者: {}", clearedCount, username);
            return ResponseEntity.ok(ApiResponse.success("清除历史连接记录成功，删除了 " + clearedCount + " 条记录", clearedCount));

        } catch (Exception e) {
            logger.error("清除历史连接记录异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("清除历史连接记录失败"));
        }
    }

    /**
     * 清除指定规则的历史连接记录
     */
    @DeleteMapping("/{id}/connections/clear-history")
    public ResponseEntity<?> clearRuleHistoryConnections(@PathVariable Long id, Authentication auth) {
        try {
            String username = auth != null ? auth.getName() : "system";

            // 检查规则是否存在
            Optional<ForwardRule> ruleOpt = forwardRuleService.findById(id);
            if (!ruleOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            int clearedCount = connectionService.clearHistoryConnectionsByRuleId(id);

            logger.info("清除规则[{}]的历史连接记录，删除数量: {}, 操作者: {}", id, clearedCount, username);
            return ResponseEntity.ok(ApiResponse.success("清除规则历史连接记录成功，删除了 " + clearedCount + " 条记录", clearedCount));

        } catch (Exception e) {
            logger.error("清除规则历史连接记录异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("清除规则历史连接记录失败"));
        }
    }

    /**
     * 获取历史连接记录统计
     */
    @GetMapping("/connections/history-stats")
    public ResponseEntity<?> getHistoryConnectionStats() {
        try {
            com.ux.relay.service.ConnectionService.HistoryConnectionStats stats = connectionService.getHistoryConnectionStats();
            return ResponseEntity.ok(ApiResponse.success("获取历史连接统计成功", stats));

        } catch (Exception e) {
            logger.error("获取历史连接统计异常", e);
            return ResponseEntity.status(500).body(ApiResponse.error("获取历史连接统计失败"));
        }
    }
    
    /**
     * API响应包装类
     */
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;
        private long timestamp;
        
        public ApiResponse() {
            this.timestamp = System.currentTimeMillis();
        }
        
        public static <T> ApiResponse<T> success(String message, T data) {
            ApiResponse<T> response = new ApiResponse<>();
            response.success = true;
            response.message = message;
            response.data = data;
            return response;
        }
        
        public static <T> ApiResponse<T> success(String message) {
            return success(message, null);
        }
        
        public static <T> ApiResponse<T> error(String message) {
            ApiResponse<T> response = new ApiResponse<>();
            response.success = false;
            response.message = message;
            return response;
        }
        
        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    /**
     * 规则详细状态类
     */
    public static class RuleDetailedStatus {
        private ForwardRule rule;
        private ConnectionPoolManager.ConnectionPoolStatus poolStatus;
        private ClientConnectionManager.ClientConnectionStats clientStats;

        public RuleDetailedStatus(ForwardRule rule,
                                 ConnectionPoolManager.ConnectionPoolStatus poolStatus,
                                 ClientConnectionManager.ClientConnectionStats clientStats) {
            this.rule = rule;
            this.poolStatus = poolStatus;
            this.clientStats = clientStats;
        }

        // Getters and Setters
        public ForwardRule getRule() { return rule; }
        public void setRule(ForwardRule rule) { this.rule = rule; }

        public ConnectionPoolManager.ConnectionPoolStatus getPoolStatus() { return poolStatus; }
        public void setPoolStatus(ConnectionPoolManager.ConnectionPoolStatus poolStatus) { this.poolStatus = poolStatus; }

        public ClientConnectionManager.ClientConnectionStats getClientStats() { return clientStats; }
        public void setClientStats(ClientConnectionManager.ClientConnectionStats clientStats) { this.clientStats = clientStats; }
    }

    /**
     * 规则状态概览类
     */
    public static class RuleStatusOverview {
        private Long ruleId;
        private String ruleName;
        private String dataSourceName;
        private String connectionStatus;
        private int activeDataSourceConnections;
        private int totalDataSourceConnections;
        private int clientConnections;
        private int reconnectionAttempts;

        public RuleStatusOverview(Long ruleId, String ruleName, String dataSourceName,
                                 String connectionStatus, int activeDataSourceConnections,
                                 int totalDataSourceConnections, int clientConnections,
                                 int reconnectionAttempts) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.dataSourceName = dataSourceName;
            this.connectionStatus = connectionStatus;
            this.activeDataSourceConnections = activeDataSourceConnections;
            this.totalDataSourceConnections = totalDataSourceConnections;
            this.clientConnections = clientConnections;
            this.reconnectionAttempts = reconnectionAttempts;
        }

        // Getters and Setters
        public Long getRuleId() { return ruleId; }
        public void setRuleId(Long ruleId) { this.ruleId = ruleId; }

        public String getRuleName() { return ruleName; }
        public void setRuleName(String ruleName) { this.ruleName = ruleName; }

        public String getDataSourceName() { return dataSourceName; }
        public void setDataSourceName(String dataSourceName) { this.dataSourceName = dataSourceName; }

        public String getConnectionStatus() { return connectionStatus; }
        public void setConnectionStatus(String connectionStatus) { this.connectionStatus = connectionStatus; }

        public int getActiveDataSourceConnections() { return activeDataSourceConnections; }
        public void setActiveDataSourceConnections(int activeDataSourceConnections) { this.activeDataSourceConnections = activeDataSourceConnections; }

        public int getTotalDataSourceConnections() { return totalDataSourceConnections; }
        public void setTotalDataSourceConnections(int totalDataSourceConnections) { this.totalDataSourceConnections = totalDataSourceConnections; }

        public int getClientConnections() { return clientConnections; }
        public void setClientConnections(int clientConnections) { this.clientConnections = clientConnections; }

        public int getReconnectionAttempts() { return reconnectionAttempts; }
        public void setReconnectionAttempts(int reconnectionAttempts) { this.reconnectionAttempts = reconnectionAttempts; }
    }

    /**
     * 规则客户端连接详情类
     */
    public static class RuleClientConnectionDetails {
        private ForwardRule rule;
        private ClientConnectionManager.ClientConnectionStats clientStats;
        private ConnectionPoolManager.ConnectionPoolStatus poolStatus;
        private List<com.ux.relay.entity.ClientListenerStatus> listenerStatuses;
        private List<com.ux.relay.entity.ConnectionInfo> activeConnections;

        public RuleClientConnectionDetails(ForwardRule rule,
                                         ClientConnectionManager.ClientConnectionStats clientStats,
                                         ConnectionPoolManager.ConnectionPoolStatus poolStatus,
                                         List<com.ux.relay.entity.ClientListenerStatus> listenerStatuses,
                                         List<com.ux.relay.entity.ConnectionInfo> activeConnections) {
            this.rule = rule;
            this.clientStats = clientStats;
            this.poolStatus = poolStatus;
            this.listenerStatuses = listenerStatuses;
            this.activeConnections = activeConnections;
        }

        // Getters and Setters
        public ForwardRule getRule() { return rule; }
        public void setRule(ForwardRule rule) { this.rule = rule; }

        public ClientConnectionManager.ClientConnectionStats getClientStats() { return clientStats; }
        public void setClientStats(ClientConnectionManager.ClientConnectionStats clientStats) { this.clientStats = clientStats; }

        public ConnectionPoolManager.ConnectionPoolStatus getPoolStatus() { return poolStatus; }
        public void setPoolStatus(ConnectionPoolManager.ConnectionPoolStatus poolStatus) { this.poolStatus = poolStatus; }

        public List<com.ux.relay.entity.ClientListenerStatus> getListenerStatuses() { return listenerStatuses; }
        public void setListenerStatuses(List<com.ux.relay.entity.ClientListenerStatus> listenerStatuses) { this.listenerStatuses = listenerStatuses; }

        public List<com.ux.relay.entity.ConnectionInfo> getActiveConnections() { return activeConnections; }
        public void setActiveConnections(List<com.ux.relay.entity.ConnectionInfo> activeConnections) { this.activeConnections = activeConnections; }
    }
}
