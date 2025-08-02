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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP转发处理器
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
public class UdpForwardingChannelHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    
    private static final Logger logger = LoggerFactory.getLogger(UdpForwardingChannelHandler.class);
    
    private final ForwardRule rule;
    private final ConnectionService connectionService;
    private final MetricsService metricsService;
    
    // 客户端会话映射 (客户端地址 -> 出站通道)
    private final Map<InetSocketAddress, Channel> clientChannels = new ConcurrentHashMap<>();
    
    // 连接信息映射 (客户端地址 -> 连接信息)
    private final Map<InetSocketAddress, ConnectionInfo> connectionInfoMap = new ConcurrentHashMap<>();
    
    public UdpForwardingChannelHandler(ForwardRule rule, 
                                      ConnectionService connectionService,
                                      MetricsService metricsService) {
        this.rule = rule;
        this.connectionService = connectionService;
        this.metricsService = metricsService;
    }
    
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        InetSocketAddress clientAddress = packet.sender();
        ByteBuf data = packet.content();
        int dataSize = data.readableBytes();
        
        logger.debug("收到UDP数据包: {} -> {}:{}, 大小: {} bytes", 
                    clientAddress, rule.getTargetIp(), rule.getTargetPort(), dataSize);
        
        // 获取或创建客户端会话
        Channel outboundChannel = getOrCreateClientChannel(ctx, clientAddress);
        
        if (outboundChannel != null && outboundChannel.isActive()) {
            // 创建目标数据包
            InetSocketAddress targetAddress = new InetSocketAddress(rule.getTargetIp(), rule.getTargetPort());
            DatagramPacket targetPacket = new DatagramPacket(data.retain(), targetAddress);
            
            // 转发数据包到目标服务器
            outboundChannel.writeAndFlush(targetPacket).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // 更新流量统计
                        ConnectionInfo connectionInfo = connectionInfoMap.get(clientAddress);
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
            data.release();
        }
    }
    
    /**
     * 获取或创建客户端会话通道
     */
    private Channel getOrCreateClientChannel(ChannelHandlerContext ctx, InetSocketAddress clientAddress) {
        Channel existingChannel = clientChannels.get(clientAddress);
        
        // 检查现有通道是否有效
        if (existingChannel != null && existingChannel.isActive()) {
            return existingChannel;
        }
        
        try {
            // 创建新的出站通道
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(ctx.channel().eventLoop())
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_RCVBUF, 65536)
                    .option(ChannelOption.SO_SNDBUF, 65536)
                    .handler(new UdpOutboundHandler(ctx.channel(), clientAddress));
            
            ChannelFuture future = bootstrap.bind(0); // 绑定到任意可用端口
            Channel outboundChannel = future.channel();
            
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        // 保存客户端会话
                        clientChannels.put(clientAddress, outboundChannel);
                        
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
                        connectionInfoMap.put(clientAddress, connectionInfo);
                        connectionService.saveConnection(connectionInfo);
                        
                        logger.info("UDP客户端会话创建成功: {} -> {}:{}", 
                                   clientAddress, rule.getTargetIp(), rule.getTargetPort());
                        
                        // 更新指标
                        metricsService.incrementActiveConnections();
                        metricsService.incrementTotalConnections();
                        
                        // 设置通道关闭监听器
                        outboundChannel.closeFuture().addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                // 清理会话
                                clientChannels.remove(clientAddress);
                                ConnectionInfo connectionInfo = connectionInfoMap.remove(clientAddress);
                                
                                if (connectionInfo != null) {
                                    connectionInfo.setStatus(ConnectionInfo.ConnectionStatus.DISCONNECTED);
                                    connectionService.updateConnection(connectionInfo);
                                }
                                
                                logger.info("UDP客户端会话关闭: {}", clientAddress);
                                metricsService.decrementActiveConnections();
                            }
                        });
                        
                    } else {
                        logger.error("UDP出站通道创建失败: {}", future.cause().getMessage());
                        metricsService.incrementConnectionErrors();
                    }
                }
            });
            
            return outboundChannel;
            
        } catch (Exception e) {
            logger.error("创建UDP客户端会话异常", e);
            metricsService.incrementConnectionErrors();
            return null;
        }
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("UDP转发异常: {}", cause.getMessage(), cause);
        metricsService.incrementConnectionErrors();
    }
    
    /**
     * UDP出站处理器（处理从目标服务器返回的数据）
     */
    private class UdpOutboundHandler extends SimpleChannelInboundHandler<DatagramPacket> {
        
        private final Channel inboundChannel;
        private final InetSocketAddress clientAddress;
        
        public UdpOutboundHandler(Channel inboundChannel, InetSocketAddress clientAddress) {
            this.inboundChannel = inboundChannel;
            this.clientAddress = clientAddress;
        }
        
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            if (inboundChannel.isActive()) {
                ByteBuf data = packet.content();
                int dataSize = data.readableBytes();
                
                // 创建回传数据包
                DatagramPacket responsePacket = new DatagramPacket(data.retain(), clientAddress);
                
                // 转发数据包到客户端
                inboundChannel.writeAndFlush(responsePacket).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            // 更新流量统计
                            ConnectionInfo connectionInfo = connectionInfoMap.get(clientAddress);
                            if (connectionInfo != null) {
                                connectionService.updateTrafficStats(
                                    connectionInfo.getConnectionId(), (long) dataSize, 0L, 1L, 0L);
                            }
                            metricsService.addBytesTransferred(dataSize);
                            
                            logger.debug("UDP数据包回传成功: {} bytes", dataSize);
                            
                        } else {
                            logger.error("UDP数据包回传失败: {}", future.cause().getMessage());
                            metricsService.incrementTransferErrors();
                        }
                    }
                });
                
            } else {
                logger.warn("UDP入站通道不可用，丢弃数据包: {} bytes", packet.content().readableBytes());
                packet.content().release();
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("UDP出站处理异常: {}", cause.getMessage(), cause);
            
            // 更新连接状态
            ConnectionInfo connectionInfo = connectionInfoMap.get(clientAddress);
            if (connectionInfo != null) {
                connectionInfo.setStatus(ConnectionInfo.ConnectionStatus.ERROR);
                connectionInfo.setErrorMessage(cause.getMessage());
                connectionService.updateConnection(connectionInfo);
            }
            
            ctx.channel().close();
            metricsService.incrementConnectionErrors();
        }
    }
}
