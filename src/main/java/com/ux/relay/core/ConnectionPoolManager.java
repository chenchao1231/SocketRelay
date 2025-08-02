package com.ux.relay.core;

import com.ux.relay.entity.ForwardRule;
import com.ux.relay.service.ConnectionService;
import com.ux.relay.service.MetricsService;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 连接池管理器
 * 负责管理到数据源服务器的连接池，实现自动重连和负载均衡
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Component
public class ConnectionPoolManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPoolManager.class);
    
    @Value("${app.forwarding.reconnection.enabled:true}")
    private boolean reconnectionEnabled;
    
    @Value("${app.forwarding.reconnection.interval:5000}")
    private long reconnectionInterval;
    
    @Value("${app.forwarding.reconnection.max-attempts:10}")
    private int maxReconnectionAttempts;
    
    @Value("${app.forwarding.connection.pool-size:5}")
    private int connectionPoolSize;
    
    // 规则ID -> 连接池
    private final Map<Long, ConnectionPool> connectionPools = new ConcurrentHashMap<>();
    
    // 重连调度器
    private final ScheduledExecutorService reconnectionScheduler = 
        new ScheduledThreadPoolExecutor(2, r -> {
            Thread t = new Thread(r, "ConnectionPool-Reconnection");
            t.setDaemon(true);
            return t;
        });
    
    private EventLoopGroup workerGroup;
    private ConnectionService connectionService;
    private MetricsService metricsService;
    private ClientConnectionManager clientConnectionManager;
    
    public void initialize(EventLoopGroup workerGroup, ConnectionService connectionService,
                          MetricsService metricsService, ClientConnectionManager clientConnectionManager) {
        this.workerGroup = workerGroup;
        this.connectionService = connectionService;
        this.metricsService = metricsService;
        this.clientConnectionManager = clientConnectionManager;
    }
    
    /**
     * 为转发规则创建连接池
     */
    public ConnectionPool createConnectionPool(ForwardRule rule) {
        ConnectionPool pool = new ConnectionPool(rule);
        connectionPools.put(rule.getId(), pool);
        
        // 初始化连接
        pool.initializeConnections();
        
        logger.info("为规则 {} 创建连接池，目标: {}:{}", 
                   rule.getRuleName(), rule.getTargetIp(), rule.getTargetPort());
        
        return pool;
    }
    
    /**
     * 获取连接池
     */
    public ConnectionPool getConnectionPool(Long ruleId) {
        return connectionPools.get(ruleId);
    }
    
    /**
     * 移除连接池
     */
    public void removeConnectionPool(Long ruleId) {
        ConnectionPool pool = connectionPools.remove(ruleId);
        if (pool != null) {
            pool.shutdown();
            logger.info("移除规则 {} 的连接池", ruleId);
        }
    }
    
    /**
     * 获取可用连接
     */
    public Channel getAvailableConnection(Long ruleId) {
        ConnectionPool pool = connectionPools.get(ruleId);
        return pool != null ? pool.getConnection() : null;
    }
    
    /**
     * 获取连接池状态
     */
    public ConnectionPoolStatus getPoolStatus(Long ruleId) {
        ConnectionPool pool = connectionPools.get(ruleId);
        return pool != null ? pool.getStatus() : null;
    }
    
    /**
     * 连接池内部类
     */
    public class ConnectionPool {
        private final ForwardRule rule;
        private final AtomicReference<Channel>[] connections;
        private final AtomicInteger connectionIndex = new AtomicInteger(0);
        private final AtomicInteger activeConnections = new AtomicInteger(0);
        private final AtomicInteger reconnectionAttempts = new AtomicInteger(0);
        private volatile boolean shutdown = false;
        
        @SuppressWarnings("unchecked")
        public ConnectionPool(ForwardRule rule) {
            this.rule = rule;
            this.connections = new AtomicReference[connectionPoolSize];
            for (int i = 0; i < connectionPoolSize; i++) {
                this.connections[i] = new AtomicReference<>();
            }
        }
        
        /**
         * 初始化连接（立即创建一个连接以接收数据源主动发送的数据）
         */
        public void initializeConnections() {
            logger.info("连接池[{}]初始化，立即创建连接以接收数据源数据，池大小: {}",
                       rule.getRuleName(), connectionPoolSize);

            // 立即创建第一个连接，用于接收数据源主动发送的数据
            createConnection(0);
        }
        
        /**
         * 创建单个连接
         */
        private void createConnection(int index) {
            if (shutdown) return;
            
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(new ConnectionPoolChannelHandler(index));
                            // 添加数据源响应处理器
                            ch.pipeline().addLast(new DataSourceResponseHandler(rule, clientConnectionManager));
                        }
                    });
            
            ChannelFuture future = bootstrap.connect(rule.getTargetIp(), rule.getTargetPort());
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        Channel channel = future.channel();
                        connections[index].set(channel);
                        activeConnections.incrementAndGet();
                        reconnectionAttempts.set(0); // 重置重连计数
                        
                        logger.debug("连接池[{}]索引[{}]连接成功: {}:{}", 
                                   rule.getRuleName(), index, rule.getTargetIp(), rule.getTargetPort());
                        
                        // 设置连接关闭监听器
                        channel.closeFuture().addListener(closeFuture -> {
                            connections[index].set(null);
                            activeConnections.decrementAndGet();
                            
                            logger.warn("连接池[{}]索引[{}]连接断开: {}:{}", 
                                       rule.getRuleName(), index, rule.getTargetIp(), rule.getTargetPort());
                            
                            // 启动重连
                            scheduleReconnection(index);
                        });
                        
                    } else {
                        logger.error("连接池[{}]索引[{}]连接失败: {}:{}, 原因: {}", 
                                   rule.getRuleName(), index, rule.getTargetIp(), rule.getTargetPort(),
                                   future.cause().getMessage());
                        
                        // 启动重连
                        scheduleReconnection(index);
                    }
                }
            });
        }
        
        /**
         * 调度重连
         */
        private void scheduleReconnection(int index) {
            if (shutdown || !reconnectionEnabled) return;
            
            int attempts = reconnectionAttempts.incrementAndGet();
            if (attempts > maxReconnectionAttempts) {
                logger.error("连接池[{}]索引[{}]重连次数超过最大限制: {}", 
                           rule.getRuleName(), index, maxReconnectionAttempts);
                return;
            }
            
            long delay = Math.min(reconnectionInterval * attempts, 60000); // 最大延迟60秒
            
            logger.info("连接池[{}]索引[{}]将在{}ms后进行第{}次重连", 
                       rule.getRuleName(), index, delay, attempts);
            
            reconnectionScheduler.schedule(() -> {
                if (!shutdown) {
                    createConnection(index);
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
        
        /**
         * 获取可用连接（按需创建）
         */
        public Channel getConnection() {
            // 首先查找现有的可用连接
            for (int i = 0; i < connectionPoolSize; i++) {
                Channel channel = connections[i].get();
                if (channel != null && channel.isActive()) {
                    return channel;
                }
            }

            // 如果没有可用连接，尝试创建新连接
            if (activeConnections.get() < connectionPoolSize) {
                // 找到一个空槽位创建连接
                for (int i = 0; i < connectionPoolSize; i++) {
                    if (connections[i].get() == null) {
                        createConnectionSync(i);
                        Channel channel = connections[i].get();
                        if (channel != null && channel.isActive()) {
                            return channel;
                        }
                        break; // 只尝试创建一个连接
                    }
                }
            }

            return null;
        }

        /**
         * 同步创建连接（用于按需创建）
         */
        private void createConnectionSync(int index) {
            if (shutdown) return;

            logger.info("按需创建连接: 连接池[{}]索引[{}]", rule.getRuleName(), index);

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(new ConnectionPoolChannelHandler(index));
                            // 添加数据源响应处理器
                            ch.pipeline().addLast(new DataSourceResponseHandler(rule, clientConnectionManager));
                        }
                    });

            try {
                ChannelFuture future = bootstrap.connect(rule.getTargetIp(), rule.getTargetPort()).sync();
                if (future.isSuccess()) {
                    Channel channel = future.channel();
                    connections[index].set(channel);
                    activeConnections.incrementAndGet();
                    reconnectionAttempts.set(0); // 重置重连计数

                    logger.info("按需连接创建成功: 连接池[{}]索引[{}] -> {}:{}",
                               rule.getRuleName(), index, rule.getTargetIp(), rule.getTargetPort());

                    // 设置连接关闭监听器
                    channel.closeFuture().addListener(closeFuture -> {
                        connections[index].set(null);
                        activeConnections.decrementAndGet();

                        logger.warn("按需连接断开: 连接池[{}]索引[{}] -> {}:{}",
                                   rule.getRuleName(), index, rule.getTargetIp(), rule.getTargetPort());

                        // 启动重连
                        scheduleReconnection(index);
                    });
                } else {
                    logger.error("按需连接创建失败: 连接池[{}]索引[{}] -> {}:{}, 原因: {}",
                               rule.getRuleName(), index, rule.getTargetIp(), rule.getTargetPort(),
                               future.cause().getMessage());
                }
            } catch (Exception e) {
                logger.error("按需连接创建异常: 连接池[{}]索引[{}] -> {}:{}, 原因: {}",
                           rule.getRuleName(), index, rule.getTargetIp(), rule.getTargetPort(), e.getMessage());
            }
        }
        
        /**
         * 获取连接池状态
         */
        public ConnectionPoolStatus getStatus() {
            return new ConnectionPoolStatus(
                rule.getId(),
                rule.getRuleName(),
                rule.getTargetIp() + ":" + rule.getTargetPort(),
                activeConnections.get(),
                connectionPoolSize,
                reconnectionAttempts.get(),
                activeConnections.get() > 0 ? "CONNECTED" : "DISCONNECTED"
            );
        }
        
        /**
         * 关闭连接池
         */
        public void shutdown() {
            shutdown = true;
            
            for (AtomicReference<Channel> connRef : connections) {
                Channel channel = connRef.get();
                if (channel != null && channel.isActive()) {
                    channel.close();
                }
            }
            
            activeConnections.set(0);
        }
        
        /**
         * 连接池通道处理器
         */
        private class ConnectionPoolChannelHandler extends ChannelInboundHandlerAdapter {
            private final int index;
            
            public ConnectionPoolChannelHandler(int index) {
                this.index = index;
            }
            
            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                logger.debug("连接池[{}]索引[{}]通道变为非活跃状态", rule.getRuleName(), index);
                super.channelInactive(ctx);
            }
            
            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                logger.error("连接池[{}]索引[{}]发生异常: {}", 
                           rule.getRuleName(), index, cause.getMessage());
                ctx.close();
            }
        }
    }
    
    /**
     * 连接池状态信息
     */
    public static class ConnectionPoolStatus {
        private final Long ruleId;
        private final String ruleName;
        private final String targetAddress;
        private final int activeConnections;
        private final int totalConnections;
        private final int reconnectionAttempts;
        private final String status;
        
        public ConnectionPoolStatus(Long ruleId, String ruleName, String targetAddress,
                                  int activeConnections, int totalConnections, 
                                  int reconnectionAttempts, String status) {
            this.ruleId = ruleId;
            this.ruleName = ruleName;
            this.targetAddress = targetAddress;
            this.activeConnections = activeConnections;
            this.totalConnections = totalConnections;
            this.reconnectionAttempts = reconnectionAttempts;
            this.status = status;
        }
        
        // Getters
        public Long getRuleId() { return ruleId; }
        public String getRuleName() { return ruleName; }
        public String getTargetAddress() { return targetAddress; }
        public int getActiveConnections() { return activeConnections; }
        public int getTotalConnections() { return totalConnections; }
        public int getReconnectionAttempts() { return reconnectionAttempts; }
        public String getStatus() { return status; }
    }
    
    /**
     * 关闭连接池管理器
     */
    public void shutdown() {
        logger.info("关闭连接池管理器...");
        
        for (ConnectionPool pool : connectionPools.values()) {
            pool.shutdown();
        }
        connectionPools.clear();
        
        reconnectionScheduler.shutdown();
        try {
            if (!reconnectionScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                reconnectionScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            reconnectionScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("连接池管理器已关闭");
    }
}
