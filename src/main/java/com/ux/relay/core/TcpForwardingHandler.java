package com.ux.relay.core;

import com.ux.relay.entity.ConnectionInfo;
import com.ux.relay.entity.ForwardRule;
import com.ux.relay.service.ConnectionService;
import com.ux.relay.service.MetricsService;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * TCP转发处理器
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
public class TcpForwardingHandler extends ChannelInboundHandlerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(TcpForwardingHandler.class);
    
    private final ForwardRule rule;
    private final ConnectionService connectionService;
    private final MetricsService metricsService;
    private final ConnectionPoolManager connectionPoolManager;
    private final ClientConnectionManager clientConnectionManager;
    private final com.ux.relay.service.ClientListenerStatusService clientListenerStatusService;
    private final com.ux.relay.service.IpAccessControlService ipAccessControlService;

    private String connectionId;
    private ConnectionInfo connectionInfo;
    
    public TcpForwardingHandler(ForwardRule rule,
                               ConnectionService connectionService,
                               MetricsService metricsService,
                               ConnectionPoolManager connectionPoolManager,
                               ClientConnectionManager clientConnectionManager,
                               com.ux.relay.service.ClientListenerStatusService clientListenerStatusService,
                               com.ux.relay.service.IpAccessControlService ipAccessControlService) {
        this.rule = rule;
        this.connectionService = connectionService;
        this.metricsService = metricsService;
        this.connectionPoolManager = connectionPoolManager;
        this.clientConnectionManager = clientConnectionManager;
        this.clientListenerStatusService = clientListenerStatusService;
        this.ipAccessControlService = ipAccessControlService;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 生成连接ID
        connectionId = UUID.randomUUID().toString();

        // 获取客户端地址信息
        InetSocketAddress clientAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        InetSocketAddress localAddress = (InetSocketAddress) ctx.channel().localAddress();

        String clientIp = clientAddress.getHostString();

        logger.info("TCP客户端连接尝试: {} -> 规则[{}]",
                   clientAddress, rule.getRuleName());

        // 检查IP访问控制
        if (!ipAccessControlService.isAccessAllowed(clientIp, rule.getId())) {
            logger.warn("TCP客户端连接被拒绝: {} -> 规则[{}], 原因: IP访问控制",
                       clientAddress, rule.getRuleName());

            // 更新指标
            metricsService.incrementConnectionErrors();

            // 关闭连接
            ctx.channel().close();
            return;
        }

        logger.info("TCP客户端连接允许: {} -> 规则[{}]",
                   clientAddress, rule.getRuleName());

        // 创建连接信息
        connectionInfo = new ConnectionInfo(
            connectionId,
            rule.getId(),
            ForwardRule.ProtocolType.TCP,
            localAddress.getPort(),
            clientIp,
            clientAddress.getPort()
        );
        connectionInfo.setStatus(ConnectionInfo.ConnectionStatus.CONNECTED);

        // 保存连接信息
        connectionService.saveConnection(connectionInfo);

        // 注册客户端连接到管理器
        clientConnectionManager.registerClientConnection(rule.getId(), connectionId, ctx.channel());

        // 更新客户端监听状态
        clientListenerStatusService.onClientConnected(rule.getId(), ForwardRule.ProtocolType.TCP);

        // 更新指标
        metricsService.incrementActiveConnections();
        metricsService.incrementTotalConnections();

        logger.info("TCP客户端连接注册成功: {}", connectionId);
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf data = (ByteBuf) msg;

        // 使用客户端连接管理器转发数据到数据源
        boolean success = clientConnectionManager.forwardToDataSource(
            rule.getId(), connectionId, data, connectionPoolManager);

        if (success) {
            // 更新流量统计
            connectionService.updateTrafficStats(connectionId, 0L, (long) data.readableBytes(), 0L, 1L);
            metricsService.addBytesTransferred(data.readableBytes());
        } else {
            logger.warn("TCP数据转发失败: 连接[{}]", connectionId);
            metricsService.incrementTransferErrors();
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("TCP客户端连接断开: {}", connectionId);

        // 注销客户端连接
        clientConnectionManager.unregisterClientConnection(rule.getId(), connectionId);

        // 更新客户端监听状态
        clientListenerStatusService.onClientDisconnected(rule.getId(), ForwardRule.ProtocolType.TCP);

        // 删除连接记录（不保留历史记录）
        if (connectionInfo != null) {
            connectionService.deleteConnection(connectionInfo.getId());
        }

        // 更新指标
        metricsService.decrementActiveConnections();
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("TCP客户端连接异常: {}", cause.getMessage(), cause);

        // 更新连接状态
        if (connectionInfo != null) {
            connectionInfo.setStatus(ConnectionInfo.ConnectionStatus.ERROR);
            connectionInfo.setErrorMessage(cause.getMessage());
            connectionService.updateConnection(connectionInfo);
        }

        // 关闭连接
        ctx.channel().close();

        // 更新指标
        metricsService.incrementConnectionErrors();
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.ALL_IDLE) {
                logger.info("TCP连接空闲超时，关闭连接: {}", connectionId);
                
                // 更新连接状态
                if (connectionInfo != null) {
                    connectionInfo.setStatus(ConnectionInfo.ConnectionStatus.TIMEOUT);
                    connectionService.updateConnection(connectionInfo);
                }
                
                ctx.channel().close();
            }
        }
        super.userEventTriggered(ctx, evt);
    }
    

}
