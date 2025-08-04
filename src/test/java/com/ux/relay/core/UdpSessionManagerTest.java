package com.ux.relay.core;

import com.ux.relay.entity.ConnectionInfo;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UDP会话管理器测试
 * 
 * @author 小白很菜
 * @version 1.0
 * @since 2025-08-04
 */
public class UdpSessionManagerTest {
    
    private UdpSessionManager sessionManager;
    
    @Mock
    private Channel mockChannel;
    
    @Mock
    private ConnectionInfo mockConnectionInfo;
    
    private InetSocketAddress testClientAddress;
    private String testSessionKey;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        sessionManager = new UdpSessionManager();
        testClientAddress = new InetSocketAddress("127.0.0.1", 12345);
        testSessionKey = UdpSessionManager.generateSessionKey(testClientAddress, 1L);

        // 模拟Channel行为
        when(mockChannel.isActive()).thenReturn(true);
        when(mockConnectionInfo.getConnectionId()).thenReturn("test-connection-id");
    }
    
    @AfterEach
    void tearDown() {
        if (sessionManager != null) {
            sessionManager.shutdown();
        }
    }
    
    @Test
    void testCreateSession() {
        // 创建会话
        UdpSessionManager.UdpSession session = sessionManager.getOrCreateSession(
            testSessionKey, testClientAddress, mockChannel, mockConnectionInfo);
        
        assertNotNull(session);
        assertEquals(testSessionKey, session.getSessionKey());
        assertEquals(testClientAddress, session.getClientAddress());
        assertEquals(mockChannel, session.getOutboundChannel());
        assertEquals(mockConnectionInfo, session.getConnectionInfo());
        assertTrue(session.isValid());
        
        // 验证统计信息
        UdpSessionManager.SessionStats stats = sessionManager.getSessionStats();
        assertEquals(1, stats.getTotalSessions());
        assertEquals(1, stats.getActiveSessions());
        assertEquals(0, stats.getExpiredSessions());
        assertEquals(1, stats.getCurrentSessions());
    }
    
    @Test
    void testGetExistingSession() {
        // 创建会话
        UdpSessionManager.UdpSession session1 = sessionManager.getOrCreateSession(
            testSessionKey, testClientAddress, mockChannel, mockConnectionInfo);
        
        // 获取相同会话
        UdpSessionManager.UdpSession session2 = sessionManager.getOrCreateSession(
            testSessionKey, testClientAddress, mockChannel, mockConnectionInfo);
        
        // 应该返回相同的会话实例
        assertSame(session1, session2);
        
        // 统计信息应该只有一个会话
        UdpSessionManager.SessionStats stats = sessionManager.getSessionStats();
        assertEquals(1, stats.getTotalSessions());
        assertEquals(1, stats.getCurrentSessions());
    }
    
    @Test
    void testRemoveSession() {
        // 创建会话
        UdpSessionManager.UdpSession session = sessionManager.getOrCreateSession(
            testSessionKey, testClientAddress, mockChannel, mockConnectionInfo);
        
        assertNotNull(session);
        
        // 移除会话
        sessionManager.removeSession(testSessionKey);
        
        // 验证会话已被移除
        UdpSessionManager.UdpSession removedSession = sessionManager.getSession(testSessionKey);
        assertNull(removedSession);
        
        // 验证统计信息
        UdpSessionManager.SessionStats stats = sessionManager.getSessionStats();
        assertEquals(1, stats.getTotalSessions());
        assertEquals(0, stats.getActiveSessions());
        assertEquals(0, stats.getCurrentSessions());
    }
    
    @Test
    void testSessionTimeout() throws InterruptedException {
        // 使用EmbeddedChannel进行测试
        EmbeddedChannel embeddedChannel = new EmbeddedChannel();
        
        // 创建会话
        UdpSessionManager.UdpSession session = sessionManager.getOrCreateSession(
            testSessionKey, testClientAddress, embeddedChannel, mockConnectionInfo);
        
        assertNotNull(session);
        assertTrue(session.isValid());
        
        // 模拟时间流逝（这里我们无法真正等待5分钟，所以使用反射或其他方式测试）
        // 在实际测试中，可以创建一个测试专用的SessionManager，使用更短的超时时间
        
        // 手动触发清理
        sessionManager.cleanupExpiredSessions();
        
        // 由于会话刚创建，不应该被清理
        UdpSessionManager.UdpSession existingSession = sessionManager.getSession(testSessionKey);
        assertNotNull(existingSession);
        
        embeddedChannel.close();
    }
    
    @Test
    void testInvalidSession() {
        // 创建一个无效的Channel
        Channel invalidChannel = mock(Channel.class);
        when(invalidChannel.isActive()).thenReturn(false);
        
        // 创建会话
        UdpSessionManager.UdpSession session = sessionManager.getOrCreateSession(
            testSessionKey, testClientAddress, invalidChannel, mockConnectionInfo);
        
        assertNotNull(session);
        assertFalse(session.isValid()); // 应该是无效的
    }
    
    @Test
    void testSessionKeyGeneration() {
        InetSocketAddress address1 = new InetSocketAddress("192.168.1.1", 8080);
        InetSocketAddress address2 = new InetSocketAddress("192.168.1.2", 8080);
        Long ruleId = 123L;
        
        String key1 = UdpSessionManager.generateSessionKey(address1, ruleId);
        String key2 = UdpSessionManager.generateSessionKey(address2, ruleId);
        String key3 = UdpSessionManager.generateSessionKey(address1, ruleId);
        
        assertNotNull(key1);
        assertNotNull(key2);
        assertNotEquals(key1, key2); // 不同地址应该生成不同的键
        assertEquals(key1, key3); // 相同地址和规则应该生成相同的键
        
        assertTrue(key1.contains("192.168.1.1"));
        assertTrue(key1.contains("8080"));
        assertTrue(key1.contains("123"));
    }
    
    @Test
    void testSessionStatsAccuracy() {
        InetSocketAddress addr1 = new InetSocketAddress("127.0.0.1", 1001);
        InetSocketAddress addr2 = new InetSocketAddress("127.0.0.1", 1002);
        InetSocketAddress addr3 = new InetSocketAddress("127.0.0.1", 1003);
        
        String key1 = UdpSessionManager.generateSessionKey(addr1, 1L);
        String key2 = UdpSessionManager.generateSessionKey(addr2, 1L);
        String key3 = UdpSessionManager.generateSessionKey(addr3, 1L);
        
        // 创建多个会话
        sessionManager.getOrCreateSession(key1, addr1, mockChannel, mockConnectionInfo);
        sessionManager.getOrCreateSession(key2, addr2, mockChannel, mockConnectionInfo);
        sessionManager.getOrCreateSession(key3, addr3, mockChannel, mockConnectionInfo);
        
        UdpSessionManager.SessionStats stats = sessionManager.getSessionStats();
        assertEquals(3, stats.getTotalSessions());
        assertEquals(3, stats.getActiveSessions());
        assertEquals(3, stats.getCurrentSessions());
        assertEquals(0, stats.getExpiredSessions());
        
        // 移除一个会话
        sessionManager.removeSession(key2);
        
        stats = sessionManager.getSessionStats();
        assertEquals(3, stats.getTotalSessions());
        assertEquals(2, stats.getActiveSessions());
        assertEquals(2, stats.getCurrentSessions());
        assertEquals(0, stats.getExpiredSessions());
    }
    
    @Test
    void testSessionUpdateLastActiveTime() throws InterruptedException {
        UdpSessionManager.UdpSession session = sessionManager.getOrCreateSession(
            testSessionKey, testClientAddress, mockChannel, mockConnectionInfo);
        
        long initialTime = session.getLastActiveTime();
        
        // 等待一小段时间
        Thread.sleep(10);
        
        // 更新活跃时间
        session.updateLastActiveTime();
        
        long updatedTime = session.getLastActiveTime();
        
        assertTrue(updatedTime > initialTime);
    }
}
