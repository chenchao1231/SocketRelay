package com.ux.relay.repository;

import com.ux.relay.entity.ConnectionInfo;
import com.ux.relay.entity.ForwardRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 连接信息数据访问接口
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Repository
public interface ConnectionInfoRepository extends JpaRepository<ConnectionInfo, Long> {
    
    /**
     * 根据连接ID查询连接信息
     * 
     * @param connectionId 连接ID
     * @return 连接信息
     */
    Optional<ConnectionInfo> findByConnectionId(String connectionId);
    
    /**
     * 根据规则ID查询连接信息
     * 
     * @param ruleId 规则ID
     * @return 连接信息列表
     */
    List<ConnectionInfo> findByRuleId(Long ruleId);
    
    /**
     * 根据连接状态查询连接信息
     * 
     * @param status 连接状态
     * @return 连接信息列表
     */
    List<ConnectionInfo> findByStatus(ConnectionInfo.ConnectionStatus status);
    
    /**
     * 查询活跃连接
     * 
     * @return 活跃连接列表
     */
    @Query("SELECT c FROM ConnectionInfo c WHERE c.status = 'CONNECTED' ORDER BY c.connectedAt DESC")
    List<ConnectionInfo> findActiveConnections();
    
    /**
     * 根据协议类型查询活跃连接
     * 
     * @param protocol 协议类型
     * @return 活跃连接列表
     */
    @Query("SELECT c FROM ConnectionInfo c WHERE c.status = 'CONNECTED' AND c.protocol = :protocol")
    List<ConnectionInfo> findActiveConnectionsByProtocol(@Param("protocol") ForwardRule.ProtocolType protocol);
    
    /**
     * 统计活跃连接数量
     * 
     * @return 活跃连接数量
     */
    @Query("SELECT COUNT(c) FROM ConnectionInfo c WHERE c.status = 'CONNECTED'")
    long countActiveConnections();
    
    /**
     * 根据协议统计活跃连接数量
     * 
     * @return 协议连接数统计
     */
    @Query("SELECT c.protocol, COUNT(c) FROM ConnectionInfo c WHERE c.status = 'CONNECTED' GROUP BY c.protocol")
    List<Object[]> countActiveConnectionsByProtocol();
    
    /**
     * 查询指定时间范围内的连接
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 连接信息列表
     */
    @Query("SELECT c FROM ConnectionInfo c WHERE c.connectedAt BETWEEN :startTime AND :endTime")
    List<ConnectionInfo> findConnectionsBetween(@Param("startTime") LocalDateTime startTime, 
                                               @Param("endTime") LocalDateTime endTime);
    
    /**
     * 查询超时的连接
     * 
     * @param timeoutThreshold 超时阈值时间
     * @return 超时连接列表
     */
    @Query("SELECT c FROM ConnectionInfo c WHERE c.status = 'CONNECTED' " +
           "AND c.lastActiveAt < :timeoutThreshold")
    List<ConnectionInfo> findTimeoutConnections(@Param("timeoutThreshold") LocalDateTime timeoutThreshold);
    
    /**
     * 更新连接的流量统计
     * 
     * @param connectionId 连接ID
     * @param bytesReceived 接收字节数
     * @param bytesSent 发送字节数
     * @param packetsReceived 接收包数
     * @param packetsSent 发送包数
     */
    @Modifying
    @Query("UPDATE ConnectionInfo c SET " +
           "c.bytesReceived = c.bytesReceived + :bytesReceived, " +
           "c.bytesSent = c.bytesSent + :bytesSent, " +
           "c.packetsReceived = c.packetsReceived + :packetsReceived, " +
           "c.packetsSent = c.packetsSent + :packetsSent, " +
           "c.lastActiveAt = CURRENT_TIMESTAMP " +
           "WHERE c.connectionId = :connectionId")
    int updateTrafficStats(@Param("connectionId") String connectionId,
                          @Param("bytesReceived") Long bytesReceived,
                          @Param("bytesSent") Long bytesSent,
                          @Param("packetsReceived") Long packetsReceived,
                          @Param("packetsSent") Long packetsSent);
    
    /**
     * 更新连接状态
     * 
     * @param connectionId 连接ID
     * @param status 新状态
     * @param errorMessage 错误信息（可选）
     */
    @Modifying
    @Query("UPDATE ConnectionInfo c SET c.status = :status, " +
           "c.errorMessage = :errorMessage, " +
           "c.disconnectedAt = CASE WHEN :status != 'CONNECTED' THEN CURRENT_TIMESTAMP ELSE c.disconnectedAt END " +
           "WHERE c.connectionId = :connectionId")
    int updateConnectionStatus(@Param("connectionId") String connectionId,
                              @Param("status") ConnectionInfo.ConnectionStatus status,
                              @Param("errorMessage") String errorMessage);
    
    /**
     * 删除指定时间之前的历史连接记录
     * 
     * @param beforeTime 时间阈值
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM ConnectionInfo c WHERE c.status != 'CONNECTED' AND c.disconnectedAt < :beforeTime")
    int deleteHistoryConnectionsBefore(@Param("beforeTime") LocalDateTime beforeTime);
    
    /**
     * 统计指定时间范围内的流量
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 流量统计 [总接收字节数, 总发送字节数, 总接收包数, 总发送包数]
     */
    @Query("SELECT SUM(c.bytesReceived), SUM(c.bytesSent), SUM(c.packetsReceived), SUM(c.packetsSent) " +
           "FROM ConnectionInfo c WHERE c.connectedAt BETWEEN :startTime AND :endTime")
    Object[] getTrafficStatsBetween(@Param("startTime") LocalDateTime startTime, 
                                   @Param("endTime") LocalDateTime endTime);
    
    /**
     * 查询最近的连接记录
     *
     * @return 最近的连接记录
     */
    @Query("SELECT c FROM ConnectionInfo c ORDER BY c.connectedAt DESC")
    List<ConnectionInfo> findRecentConnections();

    /**
     * 根据规则ID和状态查找连接
     *
     * @param ruleId 规则ID
     * @param status 连接状态
     * @return 连接列表
     */
    List<ConnectionInfo> findByRuleIdAndStatus(Long ruleId, ConnectionInfo.ConnectionStatus status);

    /**
     * 查找非指定状态的连接
     *
     * @param status 排除的连接状态
     * @return 连接列表
     */
    List<ConnectionInfo> findByStatusNot(ConnectionInfo.ConnectionStatus status);

    /**
     * 根据规则ID查找非指定状态的连接
     *
     * @param ruleId 规则ID
     * @param status 排除的连接状态
     * @return 连接列表
     */
    List<ConnectionInfo> findByRuleIdAndStatusNot(Long ruleId, ConnectionInfo.ConnectionStatus status);

    /**
     * 根据状态统计连接数
     *
     * @param status 连接状态
     * @return 连接数量
     */
    long countByStatus(ConnectionInfo.ConnectionStatus status);
}
