package com.ux.relay.repository;

import com.ux.relay.entity.ClientListenerStatus;
import com.ux.relay.entity.ForwardRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 客户端监听状态Repository
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Repository
public interface ClientListenerStatusRepository extends JpaRepository<ClientListenerStatus, Long> {
    
    /**
     * 根据规则ID查找监听状态
     * 
     * @param ruleId 规则ID
     * @return 监听状态列表
     */
    List<ClientListenerStatus> findByRuleId(Long ruleId);
    
    /**
     * 根据规则ID和协议查找监听状态
     * 
     * @param ruleId 规则ID
     * @param protocol 协议
     * @return 监听状态
     */
    Optional<ClientListenerStatus> findByRuleIdAndProtocol(Long ruleId, ForwardRule.ProtocolType protocol);
    
    /**
     * 根据监听端口和协议查找监听状态
     * 
     * @param listenPort 监听端口
     * @param protocol 协议
     * @return 监听状态
     */
    Optional<ClientListenerStatus> findByListenPortAndProtocol(Integer listenPort, ForwardRule.ProtocolType protocol);
    
    /**
     * 查找所有等待客户端连接的监听状态
     * 
     * @return 等待客户端连接的监听状态列表
     */
    @Query("SELECT c FROM ClientListenerStatus c WHERE c.status = 'WAITING_CLIENT'")
    List<ClientListenerStatus> findAllWaitingForClients();
    
    /**
     * 查找所有活跃的监听状态
     * 
     * @return 活跃的监听状态列表
     */
    @Query("SELECT c FROM ClientListenerStatus c WHERE c.status = 'ACTIVE'")
    List<ClientListenerStatus> findAllActive();
    
    /**
     * 根据状态查找监听状态
     * 
     * @param status 状态
     * @return 监听状态列表
     */
    List<ClientListenerStatus> findByStatus(ClientListenerStatus.ListenerStatus status);
    
    /**
     * 删除指定规则的所有监听状态
     * 
     * @param ruleId 规则ID
     */
    void deleteByRuleId(Long ruleId);
    
    /**
     * 统计等待客户端连接的监听器数量
     * 
     * @return 等待客户端连接的监听器数量
     */
    @Query("SELECT COUNT(c) FROM ClientListenerStatus c WHERE c.status = 'WAITING_CLIENT'")
    long countWaitingForClients();
    
    /**
     * 统计活跃的监听器数量
     * 
     * @return 活跃的监听器数量
     */
    @Query("SELECT COUNT(c) FROM ClientListenerStatus c WHERE c.status = 'ACTIVE'")
    long countActive();
}
