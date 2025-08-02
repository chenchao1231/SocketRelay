package com.ux.relay.core;

import com.ux.relay.entity.ForwardRule;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端连接管理器
 * 负责管理客户端连接，在数据源断开时缓存数据，重连成功后继续转发
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Component
public class ClientConnectionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ClientConnectionManager.class);
    
    // 规则ID -> 客户端连接映射
    private final Map<Long, Map<String, ClientConnection>> ruleClientConnections = new ConcurrentHashMap<>();

    // 规则ID -> 客户端连接计数
    private final Map<Long, AtomicInteger> ruleClientCounts = new ConcurrentHashMap<>();

    // 数据源通道 -> 客户端连接映射 (用于响应数据路由)
    private final Map<Channel, Map<String, ClientConnection>> dataSourceToClientMap = new ConcurrentHashMap<>();
    
    /**
     * 注册客户端连接
     */
    public void registerClientConnection(Long ruleId, String connectionId, Channel clientChannel) {
        Map<String, ClientConnection> clientConnections = ruleClientConnections.computeIfAbsent(
            ruleId, k -> new ConcurrentHashMap<>());
        
        ClientConnection clientConn = new ClientConnection(connectionId, clientChannel);
        clientConnections.put(connectionId, clientConn);
        
        AtomicInteger count = ruleClientCounts.computeIfAbsent(ruleId, k -> new AtomicInteger(0));
        count.incrementAndGet();
        
        logger.debug("注册客户端连接: 规则[{}], 连接[{}], 当前客户端数: {}", 
                   ruleId, connectionId, count.get());
        
        // 设置连接关闭监听器
        clientChannel.closeFuture().addListener(future -> {
            unregisterClientConnection(ruleId, connectionId);
        });
    }
    
    /**
     * 注销客户端连接
     */
    public void unregisterClientConnection(Long ruleId, String connectionId) {
        Map<String, ClientConnection> clientConnections = ruleClientConnections.get(ruleId);
        if (clientConnections != null) {
            ClientConnection clientConn = clientConnections.remove(connectionId);
            if (clientConn != null) {
                clientConn.cleanup();
                
                AtomicInteger count = ruleClientCounts.get(ruleId);
                if (count != null) {
                    int currentCount = count.decrementAndGet();
                    logger.debug("注销客户端连接: 规则[{}], 连接[{}], 当前客户端数: {}", 
                               ruleId, connectionId, currentCount);
                    
                    // 如果没有客户端连接了，清理映射
                    if (currentCount <= 0) {
                        ruleClientConnections.remove(ruleId);
                        ruleClientCounts.remove(ruleId);
                    }
                }
            }
        }
    }
    
    /**
     * 建立数据源通道与客户端的映射关系
     */
    public void mapDataSourceToClient(Channel dataSourceChannel, Long ruleId, String connectionId) {
        Map<String, ClientConnection> clientConnections = ruleClientConnections.get(ruleId);
        if (clientConnections != null) {
            ClientConnection clientConn = clientConnections.get(connectionId);
            if (clientConn != null) {
                dataSourceToClientMap.computeIfAbsent(dataSourceChannel, k -> new ConcurrentHashMap<>())
                    .put(connectionId, clientConn);
            }
        }
    }

    /**
     * 移除数据源通道映射
     */
    public void unmapDataSourceChannel(Channel dataSourceChannel) {
        dataSourceToClientMap.remove(dataSourceChannel);
    }

    /**
     * 转发数据源响应到对应的客户端
     */
    public boolean forwardDataSourceResponse(Channel dataSourceChannel, ByteBuf data) {
        // 首先尝试通过映射关系转发
        Map<String, ClientConnection> mappedClients = dataSourceToClientMap.get(dataSourceChannel);
        if (mappedClients != null && !mappedClients.isEmpty()) {
            return forwardToMappedClients(mappedClients, data);
        }

        // 如果没有映射关系，尝试广播到所有相关规则的客户端
        return broadcastToAllClients(dataSourceChannel, data);
    }

    /**
     * 转发数据源响应到指定规则的客户端
     */
    public boolean forwardDataSourceResponseToRule(Long ruleId, ByteBuf data) {
        Map<String, ClientConnection> clientConnections = ruleClientConnections.get(ruleId);
        if (clientConnections == null || clientConnections.isEmpty()) {
            logger.warn("规则[{}]没有活跃的客户端连接，无法转发数据源响应", ruleId);
            data.release();
            return false;
        }

        boolean success = false;
        for (ClientConnection clientConn : clientConnections.values()) {
            Channel clientChannel = clientConn.getClientChannel();
            if (clientChannel != null && clientChannel.isActive()) {
                clientChannel.writeAndFlush(data.retain()).addListener(future -> {
                    if (future.isSuccess()) {
                        clientConn.incrementReceivedBytes(data.readableBytes());
                        clientConn.incrementReceivedPackets();
                    } else {
                        logger.error("转发数据源响应到客户端失败: {}", future.cause().getMessage());
                    }
                });
                success = true;
            }
        }

        data.release();
        return success;
    }

    /**
     * 转发到已映射的客户端
     */
    private boolean forwardToMappedClients(Map<String, ClientConnection> clientConnections, ByteBuf data) {
        boolean success = false;

        for (ClientConnection clientConn : clientConnections.values()) {
            Channel clientChannel = clientConn.getClientChannel();
            if (clientChannel != null && clientChannel.isActive()) {
                clientChannel.writeAndFlush(data.retain()).addListener(future -> {
                    if (future.isSuccess()) {
                        clientConn.incrementReceivedBytes(data.readableBytes());
                        clientConn.incrementReceivedPackets();
                    } else {
                        logger.error("转发数据源响应到客户端失败: {}", future.cause().getMessage());
                    }
                });
                success = true;
            }
        }

        data.release();
        return success;
    }

    /**
     * 广播到所有客户端（当没有特定映射时）
     */
    private boolean broadcastToAllClients(Channel dataSourceChannel, ByteBuf data) {
        boolean success = false;

        // 遍历所有规则的客户端连接
        for (Map<String, ClientConnection> ruleClients : ruleClientConnections.values()) {
            for (ClientConnection clientConn : ruleClients.values()) {
                Channel clientChannel = clientConn.getClientChannel();
                if (clientChannel != null && clientChannel.isActive()) {
                    clientChannel.writeAndFlush(data.retain()).addListener(future -> {
                        if (future.isSuccess()) {
                            clientConn.incrementReceivedBytes(data.readableBytes());
                            clientConn.incrementReceivedPackets();
                            logger.debug("广播数据源响应到客户端成功: {} bytes", data.readableBytes());
                        } else {
                            logger.error("广播数据源响应到客户端失败: {}", future.cause().getMessage());
                        }
                    });
                    success = true;
                }
            }
        }

        if (success) {
            logger.info("数据源主动发送数据已广播到所有活跃客户端: {} bytes", data.readableBytes());
        } else {
            logger.warn("没有活跃的客户端接收数据源主动发送的数据: {} bytes", data.readableBytes());
        }

        data.release();
        return success;
    }

    /**
     * 转发数据到数据源
     */
    public boolean forwardToDataSource(Long ruleId, String connectionId, ByteBuf data,
                                     ConnectionPoolManager connectionPoolManager) {
        ClientConnection clientConn = getClientConnection(ruleId, connectionId);
        if (clientConn == null) {
            data.release();
            return false;
        }
        
        Channel dataSourceChannel = connectionPoolManager.getAvailableConnection(ruleId);
        
        if (dataSourceChannel != null && dataSourceChannel.isActive()) {
            // 建立数据源通道与客户端的映射关系
            mapDataSourceToClient(dataSourceChannel, ruleId, connectionId);

            // 数据源连接可用，直接转发
            dataSourceChannel.writeAndFlush(data.retain()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        clientConn.incrementSentBytes(data.readableBytes());
                        clientConn.incrementSentPackets();
                    } else {
                        logger.error("转发数据到数据源失败: {}", future.cause().getMessage());
                    }
                    data.release();
                }
            });
            return true;
            
        } else {
            // 数据源连接不可用，缓存数据
            boolean cached = clientConn.cacheData(data);
            if (cached) {
                logger.debug("数据源连接不可用，缓存数据: 连接[{}], 大小[{}]", 
                           connectionId, data.readableBytes());
            } else {
                logger.warn("数据缓存失败，丢弃数据: 连接[{}], 大小[{}]", 
                           connectionId, data.readableBytes());
                data.release();
            }
            return cached;
        }
    }
    
    /**
     * 转发数据到客户端
     */
    public boolean forwardToClient(Long ruleId, String connectionId, ByteBuf data) {
        ClientConnection clientConn = getClientConnection(ruleId, connectionId);
        if (clientConn == null) {
            data.release();
            return false;
        }
        
        Channel clientChannel = clientConn.getClientChannel();
        if (clientChannel != null && clientChannel.isActive()) {
            clientChannel.writeAndFlush(data.retain()).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        clientConn.incrementReceivedBytes(data.readableBytes());
                        clientConn.incrementReceivedPackets();
                    } else {
                        logger.error("转发数据到客户端失败: {}", future.cause().getMessage());
                    }
                    data.release();
                }
            });
            return true;
        } else {
            data.release();
            return false;
        }
    }
    
    /**
     * 数据源重连成功后，发送缓存的数据
     */
    public void flushCachedData(Long ruleId, ConnectionPoolManager connectionPoolManager) {
        Map<String, ClientConnection> clientConnections = ruleClientConnections.get(ruleId);
        if (clientConnections == null) return;
        
        for (ClientConnection clientConn : clientConnections.values()) {
            clientConn.flushCachedData(connectionPoolManager.getAvailableConnection(ruleId));
        }
        
        logger.info("规则[{}]数据源重连成功，已发送缓存数据", ruleId);
    }
    
    /**
     * 获取客户端连接
     */
    private ClientConnection getClientConnection(Long ruleId, String connectionId) {
        Map<String, ClientConnection> clientConnections = ruleClientConnections.get(ruleId);
        return clientConnections != null ? clientConnections.get(connectionId) : null;
    }
    
    /**
     * 获取规则的客户端连接数
     */
    public int getClientConnectionCount(Long ruleId) {
        AtomicInteger count = ruleClientCounts.get(ruleId);
        return count != null ? count.get() : 0;
    }
    
    /**
     * 获取规则的客户端连接统计
     */
    public ClientConnectionStats getClientConnectionStats(Long ruleId) {
        Map<String, ClientConnection> clientConnections = ruleClientConnections.get(ruleId);
        if (clientConnections == null) {
            return new ClientConnectionStats(0, 0, 0, 0, 0);
        }
        
        long totalReceivedBytes = 0;
        long totalSentBytes = 0;
        long totalReceivedPackets = 0;
        long totalSentPackets = 0;
        int cachedDataSize = 0;
        
        for (ClientConnection clientConn : clientConnections.values()) {
            totalReceivedBytes += clientConn.getReceivedBytes();
            totalSentBytes += clientConn.getSentBytes();
            totalReceivedPackets += clientConn.getReceivedPackets();
            totalSentPackets += clientConn.getSentPackets();
            cachedDataSize += clientConn.getCachedDataSize();
        }
        
        return new ClientConnectionStats(
            clientConnections.size(),
            totalReceivedBytes,
            totalSentBytes,
            totalReceivedPackets,
            totalSentPackets,
            cachedDataSize
        );
    }
    
    /**
     * 客户端连接内部类
     */
    private static class ClientConnection {
        private final String connectionId;
        private final Channel clientChannel;
        private final ConcurrentLinkedQueue<ByteBuf> cachedData = new ConcurrentLinkedQueue<>();
        private final AtomicLong receivedBytes = new AtomicLong(0);
        private final AtomicLong sentBytes = new AtomicLong(0);
        private final AtomicLong receivedPackets = new AtomicLong(0);
        private final AtomicLong sentPackets = new AtomicLong(0);
        private final AtomicInteger cachedDataSize = new AtomicInteger(0);
        
        private static final int MAX_CACHE_SIZE = 1024 * 1024; // 1MB缓存限制
        
        public ClientConnection(String connectionId, Channel clientChannel) {
            this.connectionId = connectionId;
            this.clientChannel = clientChannel;
        }
        
        public Channel getClientChannel() {
            return clientChannel;
        }
        
        public boolean cacheData(ByteBuf data) {
            int dataSize = data.readableBytes();
            
            // 检查缓存大小限制
            if (cachedDataSize.get() + dataSize > MAX_CACHE_SIZE) {
                return false;
            }
            
            cachedData.offer(data.retain());
            cachedDataSize.addAndGet(dataSize);
            return true;
        }
        
        public void flushCachedData(Channel dataSourceChannel) {
            if (dataSourceChannel == null || !dataSourceChannel.isActive()) {
                return;
            }
            
            ByteBuf data;
            while ((data = cachedData.poll()) != null) {
                int dataSize = data.readableBytes();
                dataSourceChannel.writeAndFlush(data).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            incrementSentBytes(dataSize);
                            incrementSentPackets();
                        }
                    }
                });
                cachedDataSize.addAndGet(-dataSize);
            }
        }
        
        public void cleanup() {
            // 释放缓存的数据
            ByteBuf data;
            while ((data = cachedData.poll()) != null) {
                data.release();
            }
            cachedDataSize.set(0);
        }
        
        public void incrementReceivedBytes(long bytes) {
            receivedBytes.addAndGet(bytes);
        }
        
        public void incrementSentBytes(long bytes) {
            sentBytes.addAndGet(bytes);
        }
        
        public void incrementReceivedPackets() {
            receivedPackets.incrementAndGet();
        }
        
        public void incrementSentPackets() {
            sentPackets.incrementAndGet();
        }
        
        public long getReceivedBytes() {
            return receivedBytes.get();
        }
        
        public long getSentBytes() {
            return sentBytes.get();
        }
        
        public long getReceivedPackets() {
            return receivedPackets.get();
        }
        
        public long getSentPackets() {
            return sentPackets.get();
        }
        
        public int getCachedDataSize() {
            return cachedDataSize.get();
        }
    }
    
    /**
     * 客户端连接统计信息
     */
    public static class ClientConnectionStats {
        private final int connectionCount;
        private final long totalReceivedBytes;
        private final long totalSentBytes;
        private final long totalReceivedPackets;
        private final long totalSentPackets;
        private final int cachedDataSize;
        
        public ClientConnectionStats(int connectionCount, long totalReceivedBytes, 
                                   long totalSentBytes, long totalReceivedPackets, 
                                   long totalSentPackets, int cachedDataSize) {
            this.connectionCount = connectionCount;
            this.totalReceivedBytes = totalReceivedBytes;
            this.totalSentBytes = totalSentBytes;
            this.totalReceivedPackets = totalReceivedPackets;
            this.totalSentPackets = totalSentPackets;
            this.cachedDataSize = cachedDataSize;
        }
        
        public ClientConnectionStats(int connectionCount, long totalReceivedBytes, 
                                   long totalSentBytes, long totalReceivedPackets, 
                                   long totalSentPackets) {
            this(connectionCount, totalReceivedBytes, totalSentBytes, 
                 totalReceivedPackets, totalSentPackets, 0);
        }
        
        // Getters
        public int getConnectionCount() { return connectionCount; }
        public long getTotalReceivedBytes() { return totalReceivedBytes; }
        public long getTotalSentBytes() { return totalSentBytes; }
        public long getTotalReceivedPackets() { return totalReceivedPackets; }
        public long getTotalSentPackets() { return totalSentPackets; }
        public int getCachedDataSize() { return cachedDataSize; }
    }
}
