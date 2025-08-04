package com.ux.relay.controller;

import com.ux.relay.core.UdpBroadcastForwardingHandler;
import com.ux.relay.core.UdpBroadcastManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * UDP广播转发控制器
 * 提供UDP广播转发的监控和管理API
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-04
 */
@RestController
@RequestMapping("/api/udp-broadcast")
public class UdpBroadcastController {
    
    private static final Logger logger = LoggerFactory.getLogger(UdpBroadcastController.class);
    
    @Autowired
    private UdpBroadcastManager udpBroadcastManager;
    
    /**
     * 获取UDP广播转发状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getBroadcastStatus() {
        try {
            int activeHandlerCount = udpBroadcastManager.getActiveHandlerCount();
            Long[] activeRuleIds = udpBroadcastManager.getActiveRuleIds();
            
            Map<String, Object> data = new HashMap<>();
            data.put("activeHandlerCount", activeHandlerCount);
            data.put("activeRuleIds", activeRuleIds);
            data.put("isEnabled", activeHandlerCount > 0);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取UDP广播转发状态失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取状态失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取指定规则的客户端信息
     */
    @GetMapping("/clients/{ruleId}")
    public ResponseEntity<Map<String, Object>> getDownstreamClients(@PathVariable Long ruleId) {
        try {
            UdpBroadcastForwardingHandler handler = udpBroadcastManager.getHandler(ruleId);

            if (handler == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "UDP广播转发服务不存在: " + ruleId);

                return ResponseEntity.status(404).body(response);
            }

            // 获取客户端统计信息
            UdpBroadcastForwardingHandler.ClientStatistics clientStats = handler.getClientStatistics();

            Map<String, Object> data = new HashMap<>();
            data.put("ruleId", ruleId);
            data.put("isActive", true);
            data.put("downstreamClientCount", clientStats.getDownstreamClientCount());
            data.put("upstreamClientCount", clientStats.getUpstreamClientCount());
            data.put("totalClientCount", clientStats.getTotalClientCount());
            data.put("totalReceivedBytes", clientStats.getTotalReceivedBytes());
            data.put("totalSentBytes", clientStats.getTotalSentBytes());
            data.put("totalReceivedPackets", clientStats.getTotalReceivedPackets());
            data.put("totalSentPackets", clientStats.getTotalSentPackets());

            // 获取客户端详细信息
            Set<UdpBroadcastForwardingHandler.DownstreamClient> downstreamClients = handler.getDownstreamClients();
            Set<UdpBroadcastForwardingHandler.DownstreamClient> upstreamClients = handler.getUpstreamClients();

            data.put("downstreamClients", downstreamClients.stream().map(client -> {
                Map<String, Object> clientInfo = new HashMap<>();
                clientInfo.put("address", client.getAddress().toString());
                clientInfo.put("subscribeTime", client.getSubscribeTime());
                clientInfo.put("lastHeartbeat", client.getLastHeartbeat());
                return clientInfo;
            }).toArray());

            data.put("upstreamClients", upstreamClients.stream().map(client -> {
                Map<String, Object> clientInfo = new HashMap<>();
                clientInfo.put("address", client.getAddress().toString());
                clientInfo.put("subscribeTime", client.getSubscribeTime());
                clientInfo.put("lastHeartbeat", client.getLastHeartbeat());
                return clientInfo;
            }).toArray());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("获取客户端信息失败: ruleId={}", ruleId, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取客户端信息失败: " + e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取UDP广播转发配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getBroadcastConfig() {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("forwardingMode", "broadcast");
            config.put("subscriptionEnabled", true);
            config.put("clientTimeoutMs", 300000);
            config.put("heartbeatIntervalMs", 60000);
            config.put("supportedCommands", new String[]{"SUBSCRIBE", "UNSUBSCRIBE", "HEARTBEAT"});
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", config);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取UDP广播转发配置失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取配置失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取UDP广播转发使用说明
     */
    @GetMapping("/usage")
    public ResponseEntity<Map<String, Object>> getUsageInstructions() {
        try {
            Map<String, Object> usage = new HashMap<>();
            usage.put("description", "UDP广播转发服务使用说明");
            
            Map<String, Object> architecture = new HashMap<>();
            architecture.put("downstreamPort", "下游客户端连接端口（规则的源端口）");
            architecture.put("upstreamPort", "上游数据接收端口（规则的目标端口）");
            architecture.put("workflow", new String[]{
                "1. 下游客户端向下游端口发送 'SUBSCRIBE' 消息进行订阅",
                "2. 系统记录下游客户端地址并返回 'SUBSCRIBED' 确认",
                "3. 上游向上游端口发送数据",
                "4. 系统将上游数据广播给所有已订阅的下游客户端",
                "5. 下游客户端定期发送 'HEARTBEAT' 消息保持连接",
                "6. 系统自动清理超时的客户端连接"
            });
            
            Map<String, Object> commands = new HashMap<>();
            commands.put("SUBSCRIBE", "订阅广播数据，客户端发送此命令后开始接收广播数据");
            commands.put("UNSUBSCRIBE", "取消订阅，客户端发送此命令后停止接收广播数据");
            commands.put("HEARTBEAT", "心跳消息，客户端定期发送以保持连接活跃");
            
            Map<String, Object> responses = new HashMap<>();
            responses.put("SUBSCRIBED", "订阅成功确认");
            responses.put("UNSUBSCRIBED", "取消订阅确认");
            responses.put("HEARTBEAT_ACK", "心跳响应");
            
            usage.put("architecture", architecture);
            usage.put("commands", commands);
            usage.put("responses", responses);
            
            Map<String, Object> example = new HashMap<>();
            example.put("clientSubscription", "echo 'SUBSCRIBE' | nc -u <server_ip> <downstream_port>");
            example.put("clientHeartbeat", "echo 'HEARTBEAT' | nc -u <server_ip> <downstream_port>");
            example.put("upstreamSend", "echo 'broadcast data' | nc -u <server_ip> <upstream_port>");
            
            usage.put("examples", example);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", usage);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取使用说明失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取使用说明失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
}
