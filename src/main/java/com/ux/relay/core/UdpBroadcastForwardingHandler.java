package com.ux.relay.core;

import com.ux.relay.entity.ConnectionInfo;
import com.ux.relay.entity.ForwardRule;
import com.ux.relay.service.ConnectionService;
import com.ux.relay.service.MetricsService;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * UDP广播转发处理器
 * 实现一对多的UDP数据广播转发
 * 
 * 架构说明：
 * - 套接字A：下游客户端管理端口，处理订阅和心跳
 * - 套接字B：上游数据接收端口，接收需要广播的数据
 * - 广播机制：将上游数据广播给所有已订阅的下游客户端
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-04
 */
public class UdpBroadcastForwardingHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(UdpBroadcastForwardingHandler.class);
    
    // 订阅命令
    private static final String SUBSCRIBE_COMMAND = "SUBSCRIBE";
    private static final String HEARTBEAT_COMMAND = "HEARTBEAT";
    private static final String UNSUBSCRIBE_COMMAND = "UNSUBSCRIBE";
    
    // 客户端超时时间（默认5分钟）
    private static final long CLIENT_TIMEOUT_MS = 5 * 60 * 1000L;
    
    // 心跳检查间隔（默认1分钟）
    private static final long HEARTBEAT_INTERVAL_MS = 60 * 1000L;
    
    private final ForwardRule rule;
    private final ConnectionService connectionService;
    private final MetricsService metricsService;
    private final UdpSessionManager sessionManager;
    
    // 下游客户端管理（连接到22222端口）
    private final Set<DownstreamClient> downstreamClients = new CopyOnWriteArraySet<>();
    private final Map<String, DownstreamClient> downstreamClientMap = new ConcurrentHashMap<>();

    // 上游客户端管理（连接到33333端口）
    private final Set<DownstreamClient> upstreamClients = new CopyOnWriteArraySet<>();
    private final Map<String, DownstreamClient> upstreamClientMap = new ConcurrentHashMap<>();
    
    // 网络通道
    private Channel downstreamChannel;  // 套接字A：下游客户端管理
    private Channel upstreamChannel;    // 套接字B：上游数据接收
    
    // 定时任务
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "UDP-Broadcast-Scheduler");
        t.setDaemon(true);
        return t;
    });
    
    public UdpBroadcastForwardingHandler(ForwardRule rule, 
                                        ConnectionService connectionService,
                                        MetricsService metricsService,
                                        UdpSessionManager sessionManager) {
        this.rule = rule;
        this.connectionService = connectionService;
        this.metricsService = metricsService;
        this.sessionManager = sessionManager;
    }
    
    /**
     * 启动UDP广播转发服务
     */
    public void start(EventLoopGroup eventLoopGroup) throws Exception {
        // 启动下游客户端管理服务（套接字A）
        startDownstreamService(eventLoopGroup);
        
        // 启动上游数据接收服务（套接字B）
        startUpstreamService(eventLoopGroup);
        
        // 启动心跳检查任务
        startHeartbeatChecker();
        
        logger.info("UDP广播转发服务启动成功 - 下游端口: {}, 上游端口: {}",
                   rule.getSourcePort(), rule.getTargetPort());
    }
    
    /**
     * 启动下游客户端管理服务（套接字A）
     */
    private void startDownstreamService(EventLoopGroup eventLoopGroup) throws Exception {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.SO_RCVBUF, 65536)
                .option(ChannelOption.SO_SNDBUF, 65536)
                .handler(new DownstreamClientHandler());
        
        ChannelFuture future = bootstrap.bind(rule.getSourcePort()).sync();
        downstreamChannel = future.channel();

        logger.info("下游客户端管理服务启动 - 端口: {}", rule.getSourcePort());
    }
    
    /**
     * 启动上游数据接收服务（套接字B）
     */
    private void startUpstreamService(EventLoopGroup eventLoopGroup) throws Exception {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_RCVBUF, 65536)
                .option(ChannelOption.SO_SNDBUF, 65536)
                .handler(new UpstreamDataHandler());
        
        ChannelFuture future = bootstrap.bind(rule.getTargetPort()).sync();
        upstreamChannel = future.channel();
        
        logger.info("上游数据接收服务启动 - 端口: {}", rule.getTargetPort());
    }
    
    /**
     * 启动心跳检查任务
     */
    private void startHeartbeatChecker() {
        scheduler.scheduleWithFixedDelay(this::checkClientHeartbeat, 
                                       HEARTBEAT_INTERVAL_MS, 
                                       HEARTBEAT_INTERVAL_MS, 
                                       TimeUnit.MILLISECONDS);
        
        logger.info("心跳检查任务启动 - 间隔: {}ms", HEARTBEAT_INTERVAL_MS);
    }
    
    /**
     * 检查客户端心跳，清理超时客户端
     */
    private void checkClientHeartbeat() {
        long currentTime = System.currentTimeMillis();
        int removedCount = 0;
        
        for (DownstreamClient client : downstreamClients) {
            if (currentTime - client.getLastHeartbeat() > CLIENT_TIMEOUT_MS) {
                removeDownstreamClient(client);
                removedCount++;
                logger.info("移除超时下游客户端: {}", client.getAddress());
            }
        }
        
        if (removedCount > 0) {
            logger.info("心跳检查完成，移除{}个超时客户端，当前活跃客户端: {}", 
                       removedCount, downstreamClients.size());
        }
    }
    
    /**
     * 添加下游客户端
     */
    private void addDownstreamClient(InetSocketAddress clientAddress) {
        String clientKey = clientAddress.toString();
        DownstreamClient existingClient = downstreamClientMap.get(clientKey);

        if (existingClient != null) {
            // 更新现有客户端的心跳时间
            existingClient.updateHeartbeat();
            logger.debug("更新下游客户端心跳: {}", clientAddress);
        } else {
            // 添加新客户端
            DownstreamClient newClient = new DownstreamClient(clientAddress);
            downstreamClients.add(newClient);
            downstreamClientMap.put(clientKey, newClient);
            
            // 创建连接记录
            createConnectionRecord(newClient);
            
            metricsService.incrementActiveConnections();
            metricsService.incrementTotalConnections();
            
            logger.info("新增下游客户端: {}, 当前客户端数: {}", clientAddress, downstreamClients.size());
        }
    }
    
    /**
     * 移除下游客户端
     */
    private void removeDownstreamClient(DownstreamClient client) {
        String clientKey = client.getAddress().toString();
        if (downstreamClients.remove(client)) {
            downstreamClientMap.remove(clientKey);

            // 更新连接状态
            if (client.getConnectionInfo() != null) {
                client.getConnectionInfo().setStatus(ConnectionInfo.ConnectionStatus.DISCONNECTED);
                connectionService.updateConnection(client.getConnectionInfo());
            }

            metricsService.decrementActiveConnections();
            logger.info("移除下游客户端: {}, 当前客户端数: {}", client.getAddress(), downstreamClients.size());
        }
    }

    /**
     * 添加上游客户端
     */
    private void addUpstreamClient(InetSocketAddress clientAddress) {
        String clientKey = clientAddress.toString();
        DownstreamClient existingClient = upstreamClientMap.get(clientKey);

        if (existingClient != null) {
            // 更新现有客户端的心跳时间
            existingClient.updateHeartbeat();
            logger.debug("更新上游客户端心跳: {}", clientAddress);
        } else {
            // 添加新客户端
            DownstreamClient newClient = new DownstreamClient(clientAddress);
            upstreamClients.add(newClient);
            upstreamClientMap.put(clientKey, newClient);

            // 创建连接记录
            createConnectionRecord(newClient);

            metricsService.incrementActiveConnections();
            metricsService.incrementTotalConnections();

            logger.info("新增上游客户端: {}, 当前客户端数: {}", clientAddress, upstreamClients.size());
        }
    }

    /**
     * 移除上游客户端
     */
    private void removeUpstreamClient(DownstreamClient client) {
        String clientKey = client.getAddress().toString();
        if (upstreamClients.remove(client)) {
            upstreamClientMap.remove(clientKey);

            // 更新连接状态
            if (client.getConnectionInfo() != null) {
                client.getConnectionInfo().setStatus(ConnectionInfo.ConnectionStatus.DISCONNECTED);
                connectionService.updateConnection(client.getConnectionInfo());
            }

            metricsService.decrementActiveConnections();
            logger.info("移除上游客户端: {}, 当前客户端数: {}", client.getAddress(), upstreamClients.size());
        }
    }

    /**
     * 获取所有上游客户端
     */
    public Set<DownstreamClient> getUpstreamClients() {
        return new HashSet<>(upstreamClients);
    }

    /**
     * 获取客户端统计信息
     */
    public ClientStatistics getClientStatistics() {
        ClientStatistics stats = new ClientStatistics();
        stats.setDownstreamClientCount(downstreamClients.size());
        stats.setUpstreamClientCount(upstreamClients.size());
        stats.setTotalClientCount(downstreamClients.size() + upstreamClients.size());

        // 计算总的数据传输量
        long totalReceivedBytes = 0;
        long totalSentBytes = 0;
        long totalReceivedPackets = 0;
        long totalSentPackets = 0;

        // 统计下游客户端数据
        for (DownstreamClient client : downstreamClients) {
            // 这里可以添加客户端级别的统计，如果需要的话
        }

        // 统计上游客户端数据
        for (DownstreamClient client : upstreamClients) {
            // 这里可以添加客户端级别的统计，如果需要的话
        }

        stats.setTotalReceivedBytes(totalReceivedBytes);
        stats.setTotalSentBytes(totalSentBytes);
        stats.setTotalReceivedPackets(totalReceivedPackets);
        stats.setTotalSentPackets(totalSentPackets);

        return stats;
    }

    /**
     * 获取下游客户端列表
     */
    public Set<DownstreamClient> getDownstreamClients() {
        return new HashSet<>(downstreamClients);
    }

    /**
     * 客户端统计信息类
     */
    public static class ClientStatistics {
        private int downstreamClientCount;
        private int upstreamClientCount;
        private int totalClientCount;
        private long totalReceivedBytes;
        private long totalSentBytes;
        private long totalReceivedPackets;
        private long totalSentPackets;

        // Getters and Setters
        public int getDownstreamClientCount() { return downstreamClientCount; }
        public void setDownstreamClientCount(int downstreamClientCount) { this.downstreamClientCount = downstreamClientCount; }

        public int getUpstreamClientCount() { return upstreamClientCount; }
        public void setUpstreamClientCount(int upstreamClientCount) { this.upstreamClientCount = upstreamClientCount; }

        public int getTotalClientCount() { return totalClientCount; }
        public void setTotalClientCount(int totalClientCount) { this.totalClientCount = totalClientCount; }

        public long getTotalReceivedBytes() { return totalReceivedBytes; }
        public void setTotalReceivedBytes(long totalReceivedBytes) { this.totalReceivedBytes = totalReceivedBytes; }

        public long getTotalSentBytes() { return totalSentBytes; }
        public void setTotalSentBytes(long totalSentBytes) { this.totalSentBytes = totalSentBytes; }

        public long getTotalReceivedPackets() { return totalReceivedPackets; }
        public void setTotalReceivedPackets(long totalReceivedPackets) { this.totalReceivedPackets = totalReceivedPackets; }

        public long getTotalSentPackets() { return totalSentPackets; }
        public void setTotalSentPackets(long totalSentPackets) { this.totalSentPackets = totalSentPackets; }
    }
    
    /**
     * 创建连接记录
     */
    private void createConnectionRecord(DownstreamClient client) {
        try {
            String connectionId = UUID.randomUUID().toString();
            ConnectionInfo connectionInfo = new ConnectionInfo(
                connectionId,
                rule.getId(),
                ForwardRule.ProtocolType.UDP,
                rule.getSourcePort(),
                client.getAddress().getHostString(),
                client.getAddress().getPort()
            );
            connectionInfo.setStatus(ConnectionInfo.ConnectionStatus.CONNECTED);
            
            connectionService.saveConnection(connectionInfo);
            client.setConnectionInfo(connectionInfo);
            
        } catch (Exception e) {
            logger.error("创建连接记录失败", e);
        }
    }
    
    /**
     * 广播数据给所有下游客户端
     */
    private void broadcastToDownstream(ByteBuf data) {
        if (downstreamClients.isEmpty()) {
            logger.debug("没有下游客户端，跳过广播");
            data.release();
            return;
        }
        
        int clientCount = downstreamClients.size();
        int dataSize = data.readableBytes();
        
        logger.debug("广播数据给{}个下游客户端，数据大小: {} bytes", clientCount, dataSize);
        
        for (DownstreamClient client : downstreamClients) {
            try {
                DatagramPacket packet = new DatagramPacket(data.retainedDuplicate(), client.getAddress());
                downstreamChannel.writeAndFlush(packet).addListener(future -> {
                    if (future.isSuccess()) {
                        // 更新流量统计
                        if (client.getConnectionInfo() != null) {
                            connectionService.updateTrafficStats(
                                client.getConnectionInfo().getConnectionId(), 
                                (long) dataSize, 0L, 1L, 0L);
                        }
                    } else {
                        logger.warn("广播数据失败: {} -> {}", client.getAddress(), future.cause().getMessage());
                    }
                });
            } catch (Exception e) {
                logger.error("广播数据异常: {}", client.getAddress(), e);
            }
        }
        
        // 释放原始数据
        data.release();
        
        // 更新指标
        metricsService.addBytesTransferred(dataSize * clientCount);
    }
    
    /**
     * 停止服务
     */
    public void stop() {
        logger.info("正在停止UDP广播转发服务...");
        
        // 停止定时任务
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 关闭网络通道
        if (downstreamChannel != null) {
            downstreamChannel.close();
        }
        if (upstreamChannel != null) {
            upstreamChannel.close();
        }
        
        // 清理下游客户端连接
        for (DownstreamClient client : downstreamClients) {
            if (client.getConnectionInfo() != null) {
                client.getConnectionInfo().setStatus(ConnectionInfo.ConnectionStatus.DISCONNECTED);
                connectionService.updateConnection(client.getConnectionInfo());
            }
        }
        downstreamClients.clear();
        downstreamClientMap.clear();

        // 清理上游客户端连接
        for (DownstreamClient client : upstreamClients) {
            if (client.getConnectionInfo() != null) {
                client.getConnectionInfo().setStatus(ConnectionInfo.ConnectionStatus.DISCONNECTED);
                connectionService.updateConnection(client.getConnectionInfo());
            }
        }
        upstreamClients.clear();
        upstreamClientMap.clear();
        
        logger.info("UDP广播转发服务已停止");
    }

    /**
     * 下游客户端处理器（套接字A）
     * 处理客户端订阅、心跳、取消订阅等消息
     */
    private class DownstreamClientHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            InetSocketAddress clientAddress = packet.sender();
            ByteBuf data = packet.content();

            try {
                String message = data.toString(StandardCharsets.UTF_8).trim();
                logger.debug("收到下游客户端消息: {} -> {}", clientAddress, message);

                if (SUBSCRIBE_COMMAND.equals(message)) {
                    // 处理订阅请求
                    addDownstreamClient(clientAddress);

                    // 发送订阅确认
                    String response = "SUBSCRIBED";
                    ByteBuf responseBuf = Unpooled.copiedBuffer(response, StandardCharsets.UTF_8);
                    DatagramPacket responsePacket = new DatagramPacket(responseBuf, clientAddress);
                    ctx.writeAndFlush(responsePacket);

                } else if (HEARTBEAT_COMMAND.equals(message)) {
                    // 处理心跳消息
                    String clientKey = clientAddress.toString();
                    DownstreamClient client = downstreamClientMap.get(clientKey);
                    if (client != null) {
                        client.updateHeartbeat();
                        logger.debug("更新客户端心跳: {}", clientAddress);

                        // 发送心跳响应
                        String response = "HEARTBEAT_ACK";
                        ByteBuf responseBuf = Unpooled.copiedBuffer(response, StandardCharsets.UTF_8);
                        DatagramPacket responsePacket = new DatagramPacket(responseBuf, clientAddress);
                        ctx.writeAndFlush(responsePacket);
                    }

                } else if (UNSUBSCRIBE_COMMAND.equals(message)) {
                    // 处理取消订阅请求
                    String clientKey = clientAddress.toString();
                    DownstreamClient client = downstreamClientMap.get(clientKey);
                    if (client != null) {
                        removeDownstreamClient(client);

                        // 发送取消订阅确认
                        String response = "UNSUBSCRIBED";
                        ByteBuf responseBuf = Unpooled.copiedBuffer(response, StandardCharsets.UTF_8);
                        DatagramPacket responsePacket = new DatagramPacket(responseBuf, clientAddress);
                        ctx.writeAndFlush(responsePacket);
                    }

                } else {
                    // 处理其他消息（数据消息 + 自动订阅机制）
                    String clientKey = clientAddress.toString();
                    DownstreamClient client = downstreamClientMap.get(clientKey);

                    if (client != null) {
                        // 已订阅的客户端，更新心跳并转发消息到服务端
                        client.updateHeartbeat();
                        logger.debug("收到已订阅客户端数据，更新心跳: {}", clientAddress);

                        // 转发消息到服务端
                        forwardToUpstream(data, clientAddress);

                    } else {
                        // 自动订阅：任何向下游端口发送数据的客户端都会被自动订阅
                        addDownstreamClient(clientAddress);
                        logger.info("自动订阅新客户端: {} -> {}", clientAddress, message);

                        // 发送自动订阅确认
                        String response = "AUTO_SUBSCRIBED";
                        ByteBuf responseBuf = Unpooled.copiedBuffer(response, StandardCharsets.UTF_8);
                        DatagramPacket responsePacket = new DatagramPacket(responseBuf, clientAddress);
                        ctx.writeAndFlush(responsePacket);

                        // 转发消息到服务端
                        forwardToUpstream(data, clientAddress);
                    }
                }

            } catch (Exception e) {
                logger.error("处理下游客户端消息异常: {}", clientAddress, e);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("下游客户端处理器异常", cause);
            metricsService.incrementTransferErrors();
        }
    }

    /**
     * 上游数据处理器（套接字B）
     * 接收上游数据并广播给所有下游客户端
     */
    private class UpstreamDataHandler extends SimpleChannelInboundHandler<DatagramPacket> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
            InetSocketAddress senderAddress = packet.sender();
            ByteBuf data = packet.content();
            int dataSize = data.readableBytes();

            // 自动订阅上游客户端
            addUpstreamClient(senderAddress);

            logger.info("收到上游数据: {} -> 广播给{}个下游客户端, 大小: {} bytes",
                        senderAddress, downstreamClients.size(), dataSize);

            try {
                // 广播数据给所有下游客户端
                broadcastToDownstream(data.retain());

                // 更新指标
                metricsService.addBytesTransferred(dataSize);

            } catch (Exception e) {
                logger.error("处理上游数据异常", e);
                data.release(); // 确保在异常情况下释放数据
                metricsService.incrementTransferErrors();
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("上游数据处理器异常", cause);
            metricsService.incrementTransferErrors();
        }
    }

    /**
     * 下游客户端信息
     */
    public static class DownstreamClient {
        private final InetSocketAddress address;
        private final long subscribeTime;
        private volatile long lastHeartbeat;
        private ConnectionInfo connectionInfo;

        public DownstreamClient(InetSocketAddress address) {
            this.address = address;
            this.subscribeTime = System.currentTimeMillis();
            this.lastHeartbeat = subscribeTime;
        }

        public void updateHeartbeat() {
            this.lastHeartbeat = System.currentTimeMillis();
        }

        // Getters and Setters
        public InetSocketAddress getAddress() { return address; }
        public long getSubscribeTime() { return subscribeTime; }
        public long getLastHeartbeat() { return lastHeartbeat; }
        public ConnectionInfo getConnectionInfo() { return connectionInfo; }
        public void setConnectionInfo(ConnectionInfo connectionInfo) { this.connectionInfo = connectionInfo; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            DownstreamClient that = (DownstreamClient) obj;
            return address.equals(that.address);
        }

        @Override
        public int hashCode() {
            return address.hashCode();
        }

        @Override
        public String toString() {
            return String.format("DownstreamClient{address=%s, subscribeTime=%d, lastHeartbeat=%d}",
                               address, subscribeTime, lastHeartbeat);
        }
    }

    /**
     * 将下游客户端消息广播给所有上游客户端
     */
    private void forwardToUpstream(ByteBuf data, InetSocketAddress clientAddress) {
        // 获取所有连接到上游端口(33333)的客户端
        Set<DownstreamClient> upstreamClients = getUpstreamClients();

        if (upstreamClients.isEmpty()) {
            logger.debug("没有上游客户端，跳过转发: {}", clientAddress);
            return;
        }

        logger.info("收到下游数据: {} -> 广播给{}个上游客户端, 大小: {} bytes",
                   clientAddress, upstreamClients.size(), data.readableBytes());

        // 广播给所有上游客户端
        int successCount = 0;
        for (DownstreamClient upstreamClient : upstreamClients) {
            try {
                // 复制数据，避免引用计数问题
                ByteBuf forwardData = data.retainedDuplicate();

                // 创建数据包并发送给上游客户端
                DatagramPacket forwardPacket = new DatagramPacket(forwardData, upstreamClient.getAddress());

                upstreamChannel.writeAndFlush(forwardPacket).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            logger.debug("成功转发下游消息给上游客户端: {} -> {}, 大小: {} bytes",
                                       clientAddress, upstreamClient.getAddress(), data.readableBytes());
                        } else {
                            logger.error("转发下游消息给上游客户端失败: {} -> {}",
                                       clientAddress, upstreamClient.getAddress(), future.cause());
                            metricsService.incrementTransferErrors();
                        }
                    }
                });

                successCount++;

            } catch (Exception e) {
                logger.error("转发下游消息给上游客户端异常: {} -> {}",
                           clientAddress, upstreamClient.getAddress(), e);
                metricsService.incrementTransferErrors();
            }
        }

        if (successCount > 0) {
            // 更新统计
            metricsService.addBytesTransferred(data.readableBytes() * successCount);
            logger.debug("广播下游数据给{}个上游客户端，数据大小: {} bytes", successCount, data.readableBytes());
        }
    }
}
