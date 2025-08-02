package com.ux.relay.core;

import com.ux.relay.entity.ForwardRule;
import com.ux.relay.service.ConnectionService;
import com.ux.relay.service.MetricsService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * TCP转发通道初始化器
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
public class TcpForwardingChannelInitializer extends ChannelInitializer<SocketChannel> {
    
    private static final Logger logger = LoggerFactory.getLogger(TcpForwardingChannelInitializer.class);
    
    private final ForwardRule rule;
    private final ConnectionService connectionService;
    private final MetricsService metricsService;
    private final ConnectionPoolManager connectionPoolManager;
    private final ClientConnectionManager clientConnectionManager;
    private final com.ux.relay.service.ClientListenerStatusService clientListenerStatusService;
    private final com.ux.relay.service.IpAccessControlService ipAccessControlService;
    
    public TcpForwardingChannelInitializer(ForwardRule rule,
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
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        
        // 添加空闲状态处理器（用于心跳检测）
        pipeline.addLast("idleStateHandler", new IdleStateHandler(0, 0, 300, TimeUnit.SECONDS));
        
        // 添加TCP转发处理器
        pipeline.addLast("tcpForwardingHandler",
                        new TcpForwardingHandler(rule, connectionService, metricsService,
                                               connectionPoolManager, clientConnectionManager,
                                               clientListenerStatusService, ipAccessControlService));
        
        logger.debug("TCP转发通道初始化完成: {}", ch.remoteAddress());
    }
}
