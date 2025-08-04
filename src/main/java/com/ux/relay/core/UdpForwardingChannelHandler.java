package com.ux.relay.core;

import com.ux.relay.entity.ConnectionInfo;
import com.ux.relay.entity.ForwardRule;
import com.ux.relay.service.ConnectionService;
import com.ux.relay.service.MetricsService;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * UDP转发通道处理器 (优化版本 - 修复内存泄漏)
 * 处理UDP数据包的转发逻辑，使用会话管理器防止内存泄漏
 *
 * @author 小白很菜
 * @version 2.0
 * @since 2025-08-04
 */
public class UdpForwardingChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(UdpForwardingChannelHandler.class);
    
    private final ForwardRule rule;
    private final ConnectionService connectionService;
    private final MetricsService metricsService;
    private final UdpSessionManager sessionManager;
    
    public UdpForwardingChannelHandler(ForwardRule rule,
                                      ConnectionService connectionService,
                                      MetricsService metricsService,
                                      UdpSessionManager sessionManager) {
        this.rule = rule;
        this.connectionService = connectionService;
        this.metricsService = metricsService;
        this.sessionManager = sessionManager;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        InetSocketAddress clientAddress = packet.sender();
        ByteBuf data = packet.content();
        int dataSize = data.readableBytes();
        
        logger.debug("收到UDP数据包: {} -> {}:{}, 大小: {} bytes", 
                    clientAddress, rule.getTargetIp(), rule.getTargetPort(), dataSize);
        
        try {
            // 生成会话键
            String sessionKey = UdpSessionManager.generateSessionKey(clientAddress, rule.getId());
            
            // 获取或创建客户端会话
            UdpSessionManager.UdpSession session = getOrCreateSession(ctx, sessionKey, clientAddress);
            
            if (session != null && session.isValid()) {
                Channel outboundChannel = session.getOutboundChannel();
                
                // 创建目标数据包
                InetSocketAddress targetAddress = new InetSocketAddress(rule.getTargetIp(), rule.getTargetPort());
                DatagramPacket targetPacket = new DatagramPacket(data.retain(), targetAddress);
                
                // 转发数据包到目标服务器
                outboundChannel.writeAndFlush(targetPacket).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            // 更新流量统计
                            ConnectionInfo connectionInfo = session.getConnectionInfo();
                            if (connectionInfo != null) {
                                connectionService.updateTrafficStats(
                                    connectionInfo.getConnectionId(), 0L, (long) dataSize, 0L, 1L);
                            }
                            metricsService.addBytesTransferred(dataSize);
                            
                            logger.debug("UDP数据包转发成功: {} bytes", dataSize);
                            
                        } else {
                            logger.error("UDP数据包转发失败: {}", future.cause().getMessage());
                            metricsService.incrementTransferErrors();
                        }
                    }
                });
                
            } else {
                logger.warn("UDP目标连接不可用，丢弃数据包: {} bytes", dataSize);
                // 注意：不要手动释放data，因为SimpleChannelInboundHandler会自动处理
            }
        } catch (Exception e) {
            logger.error("处理UDP数据包异常", e);
            // 注意：不要手动释放data，因为SimpleChannelInboundHandler会自动处理
            metricsService.incrementTransferErrors();
        }
    }
    
    /**
     * 获取或创建客户端会话
     */
    private UdpSessionManager.UdpSession getOrCreateSession(ChannelHandlerContext ctx, 
                                                           String sessionKey, 
                                                           InetSocketAddress clientAddress) {
        // 先尝试获取现有会话
        UdpSessionManager.UdpSession existingSession = sessionManager.getSession(sessionKey);
        if (existingSession != null && existingSession.isValid()) {
            return existingSession;
        }
        
        // 创建新的出站通道
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_RCVBUF, 65536)
                .option(ChannelOption.SO_SNDBUF, 65536)
                .handler(new UdpResponseHandler(ctx.channel(), clientAddress, sessionKey, sessionManager));
        
        try {
            ChannelFuture connectFuture = bootstrap.bind(0); // 绑定到任意可用端口

            // 避免在EventLoop线程中使用sync()，使用异步方式
            if (ctx.channel().eventLoop().inEventLoop()) {
                // 在EventLoop线程中，使用异步方式
                connectFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            Channel outboundChannel = future.channel();
                            completeSessionCreation(sessionKey, clientAddress, outboundChannel, ctx);
                        } else {
                            logger.error("创建UDP出站通道失败: {}", future.cause().getMessage(), future.cause());
                            metricsService.incrementConnectionErrors();
                        }
                    }
                });
                return null; // 异步创建，暂时返回null
            } else {
                // 不在EventLoop线程中，可以使用sync()
                Channel outboundChannel = connectFuture.sync().channel();
                return completeSessionCreationSync(sessionKey, clientAddress, outboundChannel, ctx);
            }

            
        } catch (Exception e) {
            logger.error("创建UDP出站通道失败: {}", e.getMessage(), e);
            metricsService.incrementConnectionErrors();
            return null;
        }
    }

    /**
     * 完成会话创建（异步版本）
     */
    private void completeSessionCreation(String sessionKey, InetSocketAddress clientAddress,
                                       Channel outboundChannel, ChannelHandlerContext ctx) {
        try {
            // 创建连接信息
            String connectionId = UUID.randomUUID().toString();
            ConnectionInfo connectionInfo = new ConnectionInfo(
                connectionId,
                rule.getId(),
                ForwardRule.ProtocolType.UDP,
                ((InetSocketAddress) ctx.channel().localAddress()).getPort(),
                clientAddress.getHostString(),
                clientAddress.getPort()
            );
            connectionInfo.setStatus(ConnectionInfo.ConnectionStatus.CONNECTED);

            // 保存连接信息
            connectionService.saveConnection(connectionInfo);

            // 创建会话
            UdpSessionManager.UdpSession session = sessionManager.getOrCreateSession(
                sessionKey, clientAddress, outboundChannel, connectionInfo);

            // 更新指标
            metricsService.incrementActiveConnections();
            metricsService.incrementTotalConnections();

            logger.info("创建UDP客户端会话: {} -> {}:{}",
                       clientAddress, rule.getTargetIp(), rule.getTargetPort());

            // 设置通道关闭监听器
            outboundChannel.closeFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    // 清理会话
                    sessionManager.removeSession(sessionKey);

                    // 更新连接状态
                    connectionInfo.setStatus(ConnectionInfo.ConnectionStatus.DISCONNECTED);
                    connectionService.updateConnection(connectionInfo);

                    metricsService.decrementActiveConnections();
                    logger.info("UDP客户端会话已关闭: {}", clientAddress);
                }
            });

        } catch (Exception e) {
            logger.error("完成会话创建失败", e);
            metricsService.incrementConnectionErrors();
        }
    }

    /**
     * 完成会话创建（同步版本）
     */
    private UdpSessionManager.UdpSession completeSessionCreationSync(String sessionKey, InetSocketAddress clientAddress,
                                                                   Channel outboundChannel, ChannelHandlerContext ctx) {
        try {
            // 创建连接信息
            String connectionId = UUID.randomUUID().toString();
            ConnectionInfo connectionInfo = new ConnectionInfo(
                connectionId,
                rule.getId(),
                ForwardRule.ProtocolType.UDP,
                ((InetSocketAddress) ctx.channel().localAddress()).getPort(),
                clientAddress.getHostString(),
                clientAddress.getPort()
            );
            connectionInfo.setStatus(ConnectionInfo.ConnectionStatus.CONNECTED);

            // 保存连接信息
            connectionService.saveConnection(connectionInfo);

            // 创建会话
            UdpSessionManager.UdpSession session = sessionManager.getOrCreateSession(
                sessionKey, clientAddress, outboundChannel, connectionInfo);

            // 更新指标
            metricsService.incrementActiveConnections();
            metricsService.incrementTotalConnections();

            logger.info("创建UDP客户端会话: {} -> {}:{}",
                       clientAddress, rule.getTargetIp(), rule.getTargetPort());

            // 设置通道关闭监听器
            outboundChannel.closeFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    // 清理会话
                    sessionManager.removeSession(sessionKey);

                    // 更新连接状态
                    connectionInfo.setStatus(ConnectionInfo.ConnectionStatus.DISCONNECTED);
                    connectionService.updateConnection(connectionInfo);

                    metricsService.decrementActiveConnections();
                    logger.info("UDP客户端会话已关闭: {}", clientAddress);
                }
            });

            return session;

        } catch (Exception e) {
            logger.error("完成会话创建失败", e);
            metricsService.incrementConnectionErrors();
            return null;
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("UDP转发处理器异常", cause);
        metricsService.incrementTransferErrors();
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("UDP转发通道已关闭");
        super.channelInactive(ctx);
    }
    
    /**
     * UDP响应处理器 (优化版本)
     */
    private static class UdpResponseHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        private static final Logger logger = LoggerFactory.getLogger(UdpResponseHandler.class);
        
        private final Channel inboundChannel;
        private final InetSocketAddress clientAddress;
        private final String sessionKey;
        private final UdpSessionManager sessionManager;
        
        public UdpResponseHandler(Channel inboundChannel, InetSocketAddress clientAddress,
                                 String sessionKey, UdpSessionManager sessionManager) {
            this.inboundChannel = inboundChannel;
            this.clientAddress = clientAddress;
            this.sessionKey = sessionKey;
            this.sessionManager = sessionManager;
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            ByteBuf data = packet.content();
            int dataSize = data.readableBytes();
            
            logger.debug("收到UDP响应数据包: {} bytes -> {}", dataSize, clientAddress);
            
            try {
                // 更新会话活跃时间
                UdpSessionManager.UdpSession session = sessionManager.getSession(sessionKey);
                if (session != null) {
                    session.updateLastActiveTime();
                    
                    // 创建响应数据包发送给客户端
                    DatagramPacket responsePacket = new DatagramPacket(data.retain(), clientAddress);
                    
                    inboundChannel.writeAndFlush(responsePacket).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (future.isSuccess()) {
                                // 更新流量统计
                                ConnectionInfo connectionInfo = session.getConnectionInfo();
                                if (connectionInfo != null) {
                                    // 这里应该调用connectionService，但为了避免循环依赖，暂时跳过
                                    logger.debug("UDP响应数据包发送成功: {} bytes", dataSize);
                                }
                            } else {
                                logger.error("UDP响应数据包发送失败: {}", future.cause().getMessage());
                            }
                        }
                    });
                } else {
                    logger.warn("会话不存在，丢弃响应数据包: {} bytes", dataSize);
                    // 注意：不要手动释放data，因为SimpleChannelInboundHandler会自动处理
                }
            } catch (Exception e) {
                logger.error("处理UDP响应数据包异常", e);
                // 注意：不要手动释放data，因为SimpleChannelInboundHandler会自动处理
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("UDP响应处理器异常", cause);
            ctx.close();
        }
    }
}
