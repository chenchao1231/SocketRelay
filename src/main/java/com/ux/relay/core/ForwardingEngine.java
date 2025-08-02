package com.ux.relay.core;

import com.ux.relay.entity.ForwardRule;
import com.ux.relay.service.ConnectionService;
import com.ux.relay.service.MetricsService;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 转发引擎核心类
 * 基于Netty实现TCP/UDP数据转发
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
@Component
public class ForwardingEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(ForwardingEngine.class);
    
    @Autowired
    private ConnectionService connectionService;
    
    @Autowired
    private MetricsService metricsService;

    @Autowired
    private ConnectionPoolManager connectionPoolManager;

    @Autowired
    private ClientConnectionManager clientConnectionManager;

    @Autowired
    private com.ux.relay.service.ClientListenerStatusService clientListenerStatusService;

    @Autowired
    private com.ux.relay.service.IpAccessControlService ipAccessControlService;
    
    // Netty配置参数
    @Value("${app.forwarding.tcp.boss-threads:1}")
    private int tcpBossThreads;
    
    @Value("${app.forwarding.tcp.worker-threads:4}")
    private int tcpWorkerThreads;
    
    @Value("${app.forwarding.udp.worker-threads:4}")
    private int udpWorkerThreads;
    
    @Value("${app.forwarding.tcp.so-backlog:1024}")
    private int soBacklog;
    
    @Value("${app.forwarding.tcp.so-keepalive:true}")
    private boolean soKeepAlive;
    
    @Value("${app.forwarding.tcp.tcp-nodelay:true}")
    private boolean tcpNoDelay;
    
    @Value("${app.forwarding.udp.so-rcvbuf:65536}")
    private int udpRcvBuf;
    
    @Value("${app.forwarding.udp.so-sndbuf:65536}")
    private int udpSndBuf;
    
    // Netty事件循环组
    private EventLoopGroup tcpBossGroup;
    private EventLoopGroup tcpWorkerGroup;
    private EventLoopGroup udpWorkerGroup;
    
    // 活跃的服务器通道
    private final Map<String, Channel> activeServers = new ConcurrentHashMap<>();
    
    // 引擎状态
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    @PostConstruct
    public void init() {
        logger.info("初始化转发引擎...");
        
        // 初始化TCP事件循环组
        tcpBossGroup = new NioEventLoopGroup(tcpBossThreads);
        tcpWorkerGroup = new NioEventLoopGroup(tcpWorkerThreads);
        
        // 初始化UDP事件循环组
        udpWorkerGroup = new NioEventLoopGroup(udpWorkerThreads);

        // 初始化连接池管理器
        connectionPoolManager.initialize(tcpWorkerGroup, connectionService, metricsService, clientConnectionManager);

        running.set(true);
        logger.info("转发引擎初始化完成");
    }
    
    @PreDestroy
    public void destroy() {
        logger.info("关闭转发引擎...");
        running.set(false);
        
        // 关闭所有活跃的服务器
        stopAllForwarding();

        // 关闭连接池管理器
        connectionPoolManager.shutdown();

        // 关闭事件循环组
        if (tcpBossGroup != null) {
            tcpBossGroup.shutdownGracefully();
        }
        if (tcpWorkerGroup != null) {
            tcpWorkerGroup.shutdownGracefully();
        }
        if (udpWorkerGroup != null) {
            udpWorkerGroup.shutdownGracefully();
        }
        
        logger.info("转发引擎已关闭");
    }
    
    /**
     * 启动转发规则
     * 
     * @param rule 转发规则
     * @return 是否启动成功
     */
    public boolean startForwarding(ForwardRule rule) {
        if (!running.get()) {
            logger.warn("转发引擎未运行，无法启动转发规则: {}", rule.getRuleName());
            return false;
        }
        
        try {
            String ruleKey = generateRuleKey(rule);
            
            // 检查是否已经启动
            if (activeServers.containsKey(ruleKey)) {
                logger.warn("转发规则已经启动: {}", rule.getRuleName());
                return true;
            }
            
            boolean success = false;
            
            // 创建连接池
            connectionPoolManager.createConnectionPool(rule);

            // 根据协议类型启动相应的服务器
            switch (rule.getProtocol()) {
                case TCP:
                    success = startTcpForwarding(rule);
                    if (success) {
                        clientListenerStatusService.createListenerStatus(rule.getId(), rule.getSourcePort(), ForwardRule.ProtocolType.TCP);
                        clientListenerStatusService.setWaitingForClients(rule.getId(), ForwardRule.ProtocolType.TCP);
                    }
                    break;
                case UDP:
                    success = startUdpForwarding(rule);
                    if (success) {
                        clientListenerStatusService.createListenerStatus(rule.getId(), rule.getSourcePort(), ForwardRule.ProtocolType.UDP);
                        clientListenerStatusService.setWaitingForClients(rule.getId(), ForwardRule.ProtocolType.UDP);
                    }
                    break;
                case TCP_UDP:
                    boolean tcpSuccess = startTcpForwarding(rule);
                    boolean udpSuccess = startUdpForwarding(rule);
                    success = tcpSuccess && udpSuccess;
                    if (tcpSuccess) {
                        clientListenerStatusService.createListenerStatus(rule.getId(), rule.getSourcePort(), ForwardRule.ProtocolType.TCP);
                        clientListenerStatusService.setWaitingForClients(rule.getId(), ForwardRule.ProtocolType.TCP);
                    }
                    if (udpSuccess) {
                        clientListenerStatusService.createListenerStatus(rule.getId(), rule.getSourcePort(), ForwardRule.ProtocolType.UDP);
                        clientListenerStatusService.setWaitingForClients(rule.getId(), ForwardRule.ProtocolType.UDP);
                    }
                    break;
            }
            
            if (success) {
                logger.info("转发规则启动成功: {} -> {}:{}", 
                           rule.getSourcePort(), rule.getTargetIp(), rule.getTargetPort());
                metricsService.incrementForwardingRuleCount();
            } else {
                logger.error("转发规则启动失败: {}", rule.getRuleName());
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("启动转发规则异常: {}", rule.getRuleName(), e);
            return false;
        }
    }
    
    /**
     * 停止转发规则
     * 
     * @param rule 转发规则
     * @return 是否停止成功
     */
    public boolean stopForwarding(ForwardRule rule) {
        try {
            String ruleKey = generateRuleKey(rule);
            boolean success = true;
            
            // 根据协议类型停止相应的服务器
            switch (rule.getProtocol()) {
                case TCP:
                    success = stopServer(ruleKey + "_TCP");
                    break;
                case UDP:
                    success = stopServer(ruleKey + "_UDP");
                    break;
                case TCP_UDP:
                    success = stopServer(ruleKey + "_TCP") && stopServer(ruleKey + "_UDP");
                    break;
            }
            
            // 移除连接池
            connectionPoolManager.removeConnectionPool(rule.getId());

            // 停止客户端监听状态
            clientListenerStatusService.stopListener(rule.getId());

            if (success) {
                logger.info("转发规则停止成功: {}", rule.getRuleName());
                metricsService.decrementForwardingRuleCount();
            } else {
                logger.error("转发规则停止失败: {}", rule.getRuleName());
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("停止转发规则异常: {}", rule.getRuleName(), e);
            return false;
        }
    }
    
    /**
     * 启动TCP转发
     */
    private boolean startTcpForwarding(ForwardRule rule) {
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(tcpBossGroup, tcpWorkerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, soBacklog)
                    .childOption(ChannelOption.SO_KEEPALIVE, soKeepAlive)
                    .childOption(ChannelOption.TCP_NODELAY, tcpNoDelay)
                    .childHandler(new TcpForwardingChannelInitializer(rule, connectionService, metricsService,
                                                                    connectionPoolManager, clientConnectionManager,
                                                                    clientListenerStatusService, ipAccessControlService));
            
            String bindAddress = rule.getSourceIp() != null ? rule.getSourceIp() : "0.0.0.0";
            ChannelFuture future = bootstrap.bind(bindAddress, rule.getSourcePort()).sync();
            
            if (future.isSuccess()) {
                String serverKey = generateRuleKey(rule) + "_TCP";
                activeServers.put(serverKey, future.channel());
                logger.info("TCP转发服务器启动成功: {}:{}", bindAddress, rule.getSourcePort());
                return true;
            } else {
                logger.error("TCP转发服务器启动失败: {}:{}", bindAddress, rule.getSourcePort());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("启动TCP转发异常", e);
            return false;
        }
    }
    
    /**
     * 启动UDP转发
     */
    private boolean startUdpForwarding(ForwardRule rule) {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(udpWorkerGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_RCVBUF, udpRcvBuf)
                    .option(ChannelOption.SO_SNDBUF, udpSndBuf)
                    .handler(new UdpForwardingChannelHandler(rule, connectionService, metricsService));
            
            String bindAddress = rule.getSourceIp() != null ? rule.getSourceIp() : "0.0.0.0";
            ChannelFuture future = bootstrap.bind(bindAddress, rule.getSourcePort()).sync();
            
            if (future.isSuccess()) {
                String serverKey = generateRuleKey(rule) + "_UDP";
                activeServers.put(serverKey, future.channel());
                logger.info("UDP转发服务器启动成功: {}:{}", bindAddress, rule.getSourcePort());
                return true;
            } else {
                logger.error("UDP转发服务器启动失败: {}:{}", bindAddress, rule.getSourcePort());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("启动UDP转发异常", e);
            return false;
        }
    }
    
    /**
     * 停止指定服务器
     */
    private boolean stopServer(String serverKey) {
        Channel channel = activeServers.remove(serverKey);
        if (channel != null && channel.isActive()) {
            try {
                channel.close().sync();
                logger.info("服务器停止成功: {}", serverKey);
                return true;
            } catch (Exception e) {
                logger.error("停止服务器异常: {}", serverKey, e);
                return false;
            }
        }
        return true;
    }
    
    /**
     * 停止所有转发
     */
    public void stopAllForwarding() {
        logger.info("停止所有转发服务器...");
        
        for (Map.Entry<String, Channel> entry : activeServers.entrySet()) {
            try {
                Channel channel = entry.getValue();
                if (channel.isActive()) {
                    channel.close().sync();
                }
            } catch (Exception e) {
                logger.error("停止服务器异常: {}", entry.getKey(), e);
            }
        }
        
        activeServers.clear();
        logger.info("所有转发服务器已停止");
    }
    
    /**
     * 生成规则键
     */
    private String generateRuleKey(ForwardRule rule) {
        return String.format("%s_%d", 
                            rule.getSourceIp() != null ? rule.getSourceIp() : "0.0.0.0", 
                            rule.getSourcePort());
    }
    
    /**
     * 获取活跃服务器数量
     */
    public int getActiveServerCount() {
        return activeServers.size();
    }
    
    /**
     * 检查引擎是否运行
     */
    public boolean isRunning() {
        return running.get();
    }
}
