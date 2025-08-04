package com.ux.relay.core;

import com.ux.relay.entity.ForwardRule;
import com.ux.relay.service.ConnectionService;
import com.ux.relay.service.MetricsService;
import com.ux.relay.config.UdpForwardingConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.embedded.EmbeddedChannel;
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
    private UdpSessionManager udpSessionManager;

    @Autowired
    private UdpBroadcastManager udpBroadcastManager;

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

    @Autowired
    private UdpForwardingConfig udpForwardingConfig;
    
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

        // 打印配置信息
        logger.info("UDP转发模式配置: {} (期望值: broadcast)", udpForwardingConfig.getMode());
        logger.info("配置读取测试 - 是否为broadcast: {}", "broadcast".equalsIgnoreCase(udpForwardingConfig.getMode()));
        logger.info("TCP Boss线程数: {}, TCP Worker线程数: {}, UDP Worker线程数: {}",
                   tcpBossThreads, tcpWorkerThreads, udpWorkerThreads);

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
            if (ruleKey == null) {
                logger.error("无法生成规则键，启动转发失败: {}", rule.getRuleName());
                return false;
            }

            // 检查是否已经启动
            if (activeServers.containsKey(ruleKey)) {
                logger.warn("转发规则已经启动: {}", rule.getRuleName());
                return true;
            }
            
            boolean success = false;

            // 根据协议类型和转发模式决定是否创建连接池
            boolean needConnectionPool = needsConnectionPool(rule);
            if (needConnectionPool) {
                logger.info("创建连接池用于规则: {} ({})", rule.getRuleName(), rule.getProtocol());
                connectionPoolManager.createConnectionPool(rule);
            } else {
                logger.info("跳过连接池创建，规则使用广播模式: {} ({})", rule.getRuleName(), rule.getProtocol());
            }

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
            if (ruleKey == null) {
                logger.error("无法生成规则键，停止转发失败: {}", rule.getRuleName());
                return false;
            }
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
                String ruleKey = generateRuleKey(rule);
                if (ruleKey == null) {
                    logger.error("无法生成规则键，TCP服务器启动失败: {}", rule.getRuleName());
                    return false;
                }
                String serverKey = ruleKey + "_TCP";
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
            logger.info("启动UDP转发 - 规则ID: {}, 转发模式: {}, 源端口: {}, 目标端口: {}",
                       rule.getId(), udpForwardingConfig.getMode(), rule.getSourcePort(), rule.getTargetPort());

            if ("broadcast".equalsIgnoreCase(udpForwardingConfig.getMode())) {
                // 使用广播模式
                logger.info("使用UDP广播模式启动转发服务");
                return startUdpBroadcastForwarding(rule);
            } else {
                // 使用点对点模式（默认）
                logger.info("使用UDP点对点模式启动转发服务");
                return startUdpPointToPointForwarding(rule);
            }
        } catch (Exception e) {
            logger.error("启动UDP转发异常", e);
            return false;
        }
    }

    /**
     * 启动UDP点对点转发（原有模式）
     */
    private boolean startUdpPointToPointForwarding(ForwardRule rule) {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(udpWorkerGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_RCVBUF, udpRcvBuf)
                    .option(ChannelOption.SO_SNDBUF, udpSndBuf)
                    .handler(new UdpForwardingChannelHandler(rule, connectionService, metricsService, udpSessionManager));

            String bindAddress = rule.getSourceIp() != null ? rule.getSourceIp() : "0.0.0.0";
            ChannelFuture future = bootstrap.bind(bindAddress, rule.getSourcePort()).sync();

            if (future.isSuccess()) {
                String ruleKey = generateRuleKey(rule);
                if (ruleKey == null) {
                    logger.error("无法生成规则键，UDP点对点服务器启动失败: {}", rule.getRuleName());
                    return false;
                }
                String serverKey = ruleKey + "_UDP";
                activeServers.put(serverKey, future.channel());
                logger.info("UDP点对点转发服务器启动成功: {}:{}", bindAddress, rule.getSourcePort());
                return true;
            } else {
                logger.error("UDP点对点转发服务器启动失败: {}:{}", bindAddress, rule.getSourcePort());
                return false;
            }

        } catch (Exception e) {
            logger.error("启动UDP点对点转发异常", e);
            return false;
        }
    }

    /**
     * 启动UDP广播转发（新模式）
     */
    private boolean startUdpBroadcastForwarding(ForwardRule rule) {
        try {
            udpBroadcastManager.startUdpBroadcast(rule, udpWorkerGroup);

            // 记录服务器信息（用于停止时清理）
            logger.debug("准备生成规则键 - 规则名: {}, 源IP: {}, 源端口: {}",
                        rule.getRuleName(), rule.getSourceIp(), rule.getSourcePort());
            String ruleKey = generateRuleKey(rule);
            logger.debug("生成的规则键: {}", ruleKey);

            if (ruleKey == null) {
                logger.error("无法生成规则键，UDP广播服务器启动失败: {}", rule.getRuleName());
                return false;
            }
            String serverKey = ruleKey + "_UDP_BROADCAST";
            logger.debug("准备存储服务器键: {}", serverKey);

            // 这里我们不存储Channel，因为广播模式使用两个Channel
            // 而是通过UdpBroadcastManager来管理
            // 注意：ConcurrentHashMap不允许null值，所以我们使用一个占位符Channel
            Channel placeholderChannel = new EmbeddedChannel();
            activeServers.put(serverKey, placeholderChannel);

            logger.info("UDP广播转发服务器启动成功: 下游端口={}, 上游端口={}",
                       rule.getSourcePort(), rule.getTargetPort());
            return true;

        } catch (Exception e) {
            logger.error("启动UDP广播转发异常", e);
            return false;
        }
    }
    
    /**
     * 停止指定服务器
     */
    private boolean stopServer(String serverKey) {
        // 检查是否是UDP广播模式
        if (serverKey.endsWith("_UDP_BROADCAST")) {
            activeServers.remove(serverKey);
            // 从serverKey中提取规则ID
            String ruleKeyPart = serverKey.replace("_UDP_BROADCAST", "");
            try {
                // 解析规则ID（假设serverKey格式为 "rule_<id>_UDP_BROADCAST"）
                String[] parts = ruleKeyPart.split("_");
                if (parts.length >= 2) {
                    Long ruleId = Long.parseLong(parts[1]);
                    udpBroadcastManager.stopUdpBroadcast(ruleId);
                    logger.info("UDP广播服务器停止成功: {}", serverKey);
                    return true;
                }
            } catch (Exception e) {
                logger.error("停止UDP广播服务器异常: {}", serverKey, e);
                return false;
            }
        }

        // 原有的点对点模式停止逻辑
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
        if (rule == null) {
            logger.error("规则对象为null，无法生成规则键");
            return null;
        }

        String sourceIp = rule.getSourceIp() != null ? rule.getSourceIp() : "0.0.0.0";
        Integer sourcePort = rule.getSourcePort();

        if (sourcePort == null) {
            logger.error("规则源端口为null，无法生成规则键: {}", rule.getRuleName());
            return null;
        }

        String ruleKey = String.format("%s_%d", sourceIp, sourcePort);
        logger.debug("生成规则键: {} -> {}", rule.getRuleName(), ruleKey);
        return ruleKey;
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

    /**
     * 判断规则是否需要连接池
     * UDP广播模式不需要连接池，其他模式需要
     */
    private boolean needsConnectionPool(ForwardRule rule) {
        if (rule.getProtocol() == ForwardRule.ProtocolType.UDP) {
            // UDP协议需要检查转发模式
            if ("broadcast".equalsIgnoreCase(udpForwardingConfig.getMode())) {
                logger.debug("UDP广播模式不需要连接池: {}", rule.getRuleName());
                return false;
            } else {
                logger.debug("UDP点对点模式需要连接池: {}", rule.getRuleName());
                return true;
            }
        } else {
            // TCP和TCP_UDP模式都需要连接池
            logger.debug("TCP模式需要连接池: {}", rule.getRuleName());
            return true;
        }
    }
}
