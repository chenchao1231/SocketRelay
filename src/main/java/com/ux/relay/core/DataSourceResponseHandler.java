package com.ux.relay.core;

import com.ux.relay.entity.ForwardRule;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据源响应处理器
 * 处理从数据源服务器返回的数据，并转发给对应的客户端
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-02
 */
public class DataSourceResponseHandler extends ChannelInboundHandlerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(DataSourceResponseHandler.class);
    
    private final ForwardRule rule;
    private final ClientConnectionManager clientConnectionManager;
    
    public DataSourceResponseHandler(ForwardRule rule, ClientConnectionManager clientConnectionManager) {
        this.rule = rule;
        this.clientConnectionManager = clientConnectionManager;
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf data = (ByteBuf) msg;

        logger.debug("收到数据源[{}:{}]响应数据: {} bytes",
                    rule.getTargetIp(), rule.getTargetPort(), data.readableBytes());

        // 优先使用规则ID进行精确路由
        boolean success = clientConnectionManager.forwardDataSourceResponseToRule(rule.getId(), data);

        if (!success) {
            logger.warn("转发数据源[{}:{}]响应失败，规则[{}]没有活跃的客户端连接",
                       rule.getTargetIp(), rule.getTargetPort(), rule.getId());
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("数据源连接断开: {}:{}", rule.getTargetIp(), rule.getTargetPort());

        // 移除数据源通道映射
        clientConnectionManager.unmapDataSourceChannel(ctx.channel());

        super.channelInactive(ctx);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("数据源连接异常: {}", cause.getMessage(), cause);
        ctx.close();
    }
}
