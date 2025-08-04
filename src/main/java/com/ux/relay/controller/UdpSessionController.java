package com.ux.relay.controller;

import com.ux.relay.core.UdpSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * UDP会话管理控制器
 * 提供UDP会话监控和管理API
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-04
 */
@RestController
@RequestMapping("/api/udp-sessions")
public class UdpSessionController {
    
    private static final Logger logger = LoggerFactory.getLogger(UdpSessionController.class);
    
    @Autowired
    private UdpSessionManager udpSessionManager;
    
    /**
     * 获取UDP会话统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSessionStats() {
        try {
            UdpSessionManager.SessionStats stats = udpSessionManager.getSessionStats();
            
            Map<String, Object> data = new HashMap<>();
            data.put("totalSessions", stats.getTotalSessions());
            data.put("activeSessions", stats.getActiveSessions());
            data.put("expiredSessions", stats.getExpiredSessions());
            data.put("currentSessions", stats.getCurrentSessions());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);

            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取UDP会话统计失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取会话统计失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 手动清理过期会话
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupExpiredSessions() {
        try {
            // 获取清理前的统计
            UdpSessionManager.SessionStats beforeStats = udpSessionManager.getSessionStats();
            
            // 执行清理
            udpSessionManager.cleanupExpiredSessions();
            
            // 获取清理后的统计
            UdpSessionManager.SessionStats afterStats = udpSessionManager.getSessionStats();
            
            long cleanedCount = beforeStats.getCurrentSessions() - afterStats.getCurrentSessions();
            
            Map<String, Object> data = new HashMap<>();
            data.put("cleanedSessions", cleanedCount);
            data.put("remainingSessions", afterStats.getCurrentSessions());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "会话清理完成");
            response.put("data", data);
            
            logger.info("手动清理UDP会话: {}个", cleanedCount);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("清理UDP会话失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "清理会话失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取会话健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getSessionHealth() {
        try {
            UdpSessionManager.SessionStats stats = udpSessionManager.getSessionStats();
            
            // 计算健康指标
            double expiredRate = stats.getTotalSessions() > 0 ? 
                (double) stats.getExpiredSessions() / stats.getTotalSessions() * 100 : 0;
            
            String healthStatus;
            if (expiredRate < 10) {
                healthStatus = "HEALTHY";
            } else if (expiredRate < 30) {
                healthStatus = "WARNING";
            } else {
                healthStatus = "CRITICAL";
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("healthStatus", healthStatus);
            data.put("expiredRate", String.format("%.2f%%", expiredRate));
            data.put("activeSessions", stats.getActiveSessions());
            data.put("totalSessions", stats.getTotalSessions());
            data.put("recommendations", getHealthRecommendations(healthStatus, stats));

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", data);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("获取会话健康状态失败", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取健康状态失败: " + e.getMessage());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * 获取健康建议
     */
    private String[] getHealthRecommendations(String healthStatus, UdpSessionManager.SessionStats stats) {
        switch (healthStatus) {
            case "HEALTHY":
                return new String[]{"系统运行正常", "会话管理良好"};
            case "WARNING":
                return new String[]{
                    "过期会话比例较高，建议检查网络连接质量",
                    "考虑调整会话超时时间",
                    "监控客户端连接稳定性"
                };
            case "CRITICAL":
                return new String[]{
                    "过期会话比例过高，需要立即检查",
                    "检查网络连接和服务器性能",
                    "考虑增加清理频率",
                    "检查是否存在异常客户端"
                };
            default:
                return new String[]{"状态未知"};
        }
    }
}
