package com.ux.relay.service;

import com.ux.relay.entity.ClientListenerStatus;
import com.ux.relay.entity.ForwardRule;
import com.ux.relay.repository.ClientListenerStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 客户端监听状态服务
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Service
public class ClientListenerStatusService {
    
    private static final Logger logger = LoggerFactory.getLogger(ClientListenerStatusService.class);
    
    @Autowired
    private ClientListenerStatusRepository clientListenerStatusRepository;
    
    /**
     * 创建监听状态
     * 
     * @param ruleId 规则ID
     * @param listenPort 监听端口
     * @param protocol 协议
     * @return 监听状态
     */
    @Transactional
    public ClientListenerStatus createListenerStatus(Long ruleId, Integer listenPort, ForwardRule.ProtocolType protocol) {
        ClientListenerStatus status = new ClientListenerStatus(ruleId, listenPort, protocol);
        ClientListenerStatus saved = clientListenerStatusRepository.save(status);
        
        logger.info("创建客户端监听状态: 规则[{}], 端口[{}], 协议[{}]", ruleId, listenPort, protocol);
        return saved;
    }
    
    /**
     * 更新监听状态为等待客户端
     *
     * @param ruleId 规则ID
     * @param protocol 协议
     */
    @Transactional
    public void setWaitingForClients(Long ruleId, ForwardRule.ProtocolType protocol) {
        // 先删除可能存在的重复记录
        List<ClientListenerStatus> existingStatuses = clientListenerStatusRepository.findByRuleId(ruleId);
        for (ClientListenerStatus existing : existingStatuses) {
            if (existing.getProtocol().equals(protocol)) {
                clientListenerStatusRepository.delete(existing);
            }
        }

        // 创建新的监听状态
        ClientListenerStatus status = new ClientListenerStatus(ruleId, getListenPortByRuleId(ruleId), protocol);
        status.startWaiting();
        clientListenerStatusRepository.save(status);

        logger.info("监听状态更新为等待客户端: 规则[{}], 协议[{}]", ruleId, protocol);
    }

    /**
     * 根据规则ID获取监听端口（临时方法，应该从规则中获取）
     */
    private Integer getListenPortByRuleId(Long ruleId) {
        // 这里应该从ForwardRule中获取端口，暂时返回默认值
        return 8080; // 临时值，实际应该从规则中获取
    }
    
    /**
     * 客户端连接事件
     *
     * @param ruleId 规则ID
     * @param protocol 协议
     */
    @Transactional
    public void onClientConnected(Long ruleId, ForwardRule.ProtocolType protocol) {
        List<ClientListenerStatus> statusList = clientListenerStatusRepository.findByRuleId(ruleId);
        ClientListenerStatus targetStatus = null;

        for (ClientListenerStatus status : statusList) {
            if (status.getProtocol().equals(protocol)) {
                targetStatus = status;
                break;
            }
        }

        if (targetStatus != null) {
            targetStatus.onClientConnected();
            clientListenerStatusRepository.save(targetStatus);

            logger.info("客户端连接事件: 规则[{}], 协议[{}], 当前连接数[{}]",
                       ruleId, protocol, targetStatus.getCurrentClientCount());
        }
    }
    
    /**
     * 客户端断开事件
     *
     * @param ruleId 规则ID
     * @param protocol 协议
     */
    @Transactional
    public void onClientDisconnected(Long ruleId, ForwardRule.ProtocolType protocol) {
        List<ClientListenerStatus> statusList = clientListenerStatusRepository.findByRuleId(ruleId);
        ClientListenerStatus targetStatus = null;

        for (ClientListenerStatus status : statusList) {
            if (status.getProtocol().equals(protocol)) {
                targetStatus = status;
                break;
            }
        }

        if (targetStatus != null) {
            targetStatus.onClientDisconnected();
            clientListenerStatusRepository.save(targetStatus);

            logger.info("客户端断开事件: 规则[{}], 协议[{}], 当前连接数[{}]",
                       ruleId, protocol, targetStatus.getCurrentClientCount());
        }
    }
    
    /**
     * 停止监听
     * 
     * @param ruleId 规则ID
     */
    @Transactional
    public void stopListener(Long ruleId) {
        List<ClientListenerStatus> statusList = clientListenerStatusRepository.findByRuleId(ruleId);
        for (ClientListenerStatus status : statusList) {
            status.stop();
            clientListenerStatusRepository.save(status);
        }
        
        logger.info("停止监听: 规则[{}]", ruleId);
    }
    
    /**
     * 删除监听状态
     * 
     * @param ruleId 规则ID
     */
    @Transactional
    public void deleteListenerStatus(Long ruleId) {
        clientListenerStatusRepository.deleteByRuleId(ruleId);
        logger.info("删除监听状态: 规则[{}]", ruleId);
    }
    
    /**
     * 获取规则的监听状态
     * 
     * @param ruleId 规则ID
     * @return 监听状态列表
     */
    @Transactional(readOnly = true)
    public List<ClientListenerStatus> getListenerStatus(Long ruleId) {
        return clientListenerStatusRepository.findByRuleId(ruleId);
    }
    
    /**
     * 获取所有等待客户端连接的监听状态
     * 
     * @return 等待客户端连接的监听状态列表
     */
    @Transactional(readOnly = true)
    public List<ClientListenerStatus> getAllWaitingForClients() {
        return clientListenerStatusRepository.findAllWaitingForClients();
    }
    
    /**
     * 获取所有活跃的监听状态
     * 
     * @return 活跃的监听状态列表
     */
    @Transactional(readOnly = true)
    public List<ClientListenerStatus> getAllActive() {
        return clientListenerStatusRepository.findAllActive();
    }
    
    /**
     * 获取监听状态统计
     * 
     * @return 监听状态统计
     */
    @Transactional(readOnly = true)
    public ListenerStatusStats getListenerStatusStats() {
        long waitingCount = clientListenerStatusRepository.countWaitingForClients();
        long activeCount = clientListenerStatusRepository.countActive();
        
        return new ListenerStatusStats(waitingCount, activeCount);
    }
    
    /**
     * 监听状态统计类
     */
    public static class ListenerStatusStats {
        private final long waitingForClientsCount;
        private final long activeListenersCount;
        
        public ListenerStatusStats(long waitingForClientsCount, long activeListenersCount) {
            this.waitingForClientsCount = waitingForClientsCount;
            this.activeListenersCount = activeListenersCount;
        }
        
        public long getWaitingForClientsCount() { return waitingForClientsCount; }
        public long getActiveListenersCount() { return activeListenersCount; }
        public long getTotalListenersCount() { return waitingForClientsCount + activeListenersCount; }
    }
}
