# Socket Relay - TCP/UDP转发与管理系统

[![Java](https://img.shields.io/badge/Java-8+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.12-green.svg)](https://spring.io/projects/spring-boot)
[![Netty](https://img.shields.io/badge/Netty-4.1.65-blue.svg)](https://netty.io/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 项目简介

Socket Relay 是一个基于 Spring Boot 和 Netty 开发的 TCP/UDP 转发与管理系统，提供了完整的 Web 管理界面和实时监控功能。

### 主要特性

- 🚀 **高性能转发**: 基于 Netty 异步 I/O，支持高并发连接
- 🌐 **协议支持**: 支持 TCP、UDP 以及 TCP+UDP 混合转发
- 🔄 **智能重连**: 数据源断开时自动重连，客户端连接保持不断开
- 📊 **实时监控**: 提供连接状态、流量统计、性能指标的实时监控
- 🔧 **Web 管理**: 零代码配置，通过 Web 界面管理转发规则
- 🔔 **智能告警**: 支持连接异常、错误率过高等多种告警
- 📝 **审计日志**: 完整的操作日志记录和审计功能
- 🔒 **安全控制**: 用户认证、权限管理、IP 白名单等安全功能
- 💾 **轻量级**: 使用 H2 数据库，单 JAR 部署，无外部依赖
- 🏊 **连接池管理**: 为每个转发规则维护独立的数据源连接池
- 📦 **数据缓存**: 数据源断开时缓存客户端数据，重连后自动发送

## 系统架构

```
┌─────────────┐
│  Web浏览器   │ ← 用户访问配置与监控
└────┬────────┘
     │ HTTP/HTTPS
┌────▼──────────┐       TCP/UDP连接
│   后端服务     │◄────────────────────────────────┐
│（Spring Boot）│                              │
│   ├─ REST API │                              │
│   ├─ 转发管理 │ ──────> 转发引擎 (Netty/NIO) ─┤
│   └─ 数据管理 │                              │
└───────────────┘                              │
     │                                          │
     ▼                                          ▼
┌──────────────┐                         ┌──────────────┐
│   H2 数据库   │                         │ 客户端/数据源│
└──────────────┘                         └──────────────┘
```

## 快速开始

### 环境要求

- JDK 8 或更高版本
- Maven 3.6+
- 现代浏览器（Chrome 90+、Edge 90+、Firefox 88+）

### 编译运行

1. **克隆项目**
```bash
git clone <repository-url>
cd SocketRelay
```

2. **编译项目**
```bash
mvn clean package -DskipTests
```

3. **运行应用**
```bash
java -jar target/socketRelay-0.0.1-SNAPSHOT.jar
```

4. **访问系统**
- 管理界面: http://localhost:8080
- 默认账号: admin / admin123

### Docker 部署

```bash
# 构建镜像
docker build -t socket-relay .

# 运行容器
docker run -d -p 8080:8080 -v ./data:/app/data socket-relay
```

## 功能说明

### 🔄 智能重连机制

系统采用了先进的连接池管理和智能重连机制，解决了传统转发系统的痛点：

**问题解决**：
- ❌ **传统方案**: 数据源服务器断开时，客户端连接也被强制关闭
- ✅ **新方案**: 数据源断开时，客户端连接保持，数据被缓存，重连成功后继续转发

**核心特性**：
- **连接池管理**: 为每个转发规则维护独立的数据源连接池
- **自动重连**: 数据源断开时按配置的间隔和次数自动重连
- **数据缓存**: 重连期间缓存客户端数据，避免数据丢失
- **状态监控**: 实时显示连接池状态、重连尝试次数等信息

**配置参数**：
- `autoReconnect`: 是否启用自动重连（默认：true）
- `reconnectInterval`: 重连间隔时间，单位秒（默认：5秒）
- `maxReconnectAttempts`: 最大重连次数（默认：10次）
- `connectionPoolSize`: 连接池大小（默认：3个连接）

### 转发规则管理

- **创建规则**: 配置源端口、目标地址、协议类型、数据源名称等
- **启用/禁用**: 动态控制规则的启用状态
- **规则验证**: 自动检测端口冲突和配置错误
- **批量操作**: 支持批量启用、禁用、删除规则
- **高级配置**: 支持自动重连、重连间隔、最大重连次数、连接池大小等配置
- **状态监控**: 实时显示数据源连接状态、客户端连接数、重连尝试次数

### 连接监控

- **实时连接**: 查看当前活跃的 TCP/UDP 连接
- **流量统计**: 实时显示每个连接的流量数据
- **连接历史**: 查看历史连接记录和统计信息
- **性能指标**: CPU、内存、网络等系统性能监控
- **连接池状态**: 监控数据源连接池的健康状态
- **客户端统计**: 显示每个规则的客户端连接数和流量统计

### 告警系统

- **错误率告警**: 当连接错误率超过阈值时自动告警
- **连接数告警**: 当活跃连接数过高时告警
- **系统性能告警**: CPU、内存使用率过高告警
- **实时推送**: 通过 WebSocket 实时推送告警信息

### 安全功能

- **用户认证**: 基于 Spring Security 的用户认证
- **会话管理**: 自动登出、会话超时控制
- **操作审计**: 记录所有用户操作和系统事件
- **数据脱敏**: 敏感信息自动脱敏处理

## 配置说明

### 应用配置

主要配置文件: `src/main/resources/application.yml`

```yaml
# 服务器配置
server:
  port: 8080

# 转发引擎配置
app:
  forwarding:
    tcp:
      worker-threads: 4
      so-keepalive: true
    udp:
      worker-threads: 4
    connection:
      max-connections: 10000
      idle-timeout: 300000
```

### 性能调优

1. **线程池配置**
   - TCP Boss 线程: 通常设置为 1
   - TCP Worker 线程: 建议设置为 CPU 核心数的 2-4 倍
   - UDP Worker 线程: 建议设置为 CPU 核心数的 2-4 倍

2. **内存配置**
   ```bash
   java -Xms1g -Xmx2g -jar socketRelay-0.0.1-SNAPSHOT.jar
   ```

3. **网络优化**
   - 调整系统的 TCP/UDP 缓冲区大小
   - 优化网络接口的中断处理

## API 文档

### 转发规则 API

- `GET /api/rules` - 获取转发规则列表
- `POST /api/rules` - 创建转发规则
- `PUT /api/rules/{id}` - 更新转发规则
- `DELETE /api/rules/{id}` - 删除转发规则
- `POST /api/rules/{id}/enable` - 启用转发规则
- `POST /api/rules/{id}/disable` - 禁用转发规则
- `GET /api/rules/{id}/status` - 获取转发规则详细状态
- `GET /api/rules/status-overview` - 获取所有规则状态概览

### 连接管理 API

- `GET /api/connections/active` - 获取活跃连接
- `GET /api/connections/statistics` - 获取连接统计
- `GET /api/connections/{id}` - 获取连接详情

### 监控指标 API

- `GET /api/metrics/current` - 获取当前指标
- `GET /api/metrics/history` - 获取历史指标
- `GET /api/metrics/system-performance` - 获取系统性能

## 性能指标

### 测试环境
- CPU: 4 Core 2.4 GHz
- 内存: 8 GB
- 网络: 千兆以太网

### 性能数据
- **并发连接**: ≥10,000 TCP 连接
- **吞吐量**: ≥1 Gbps
- **延迟**: ≤2 ms（局域网环境）
- **CPU 占用**: ≤40%（1 Gbps 流量）
- **内存占用**: ≤2 GB（10,000 并发）

## 故障排除

### 常见问题

1. **端口被占用**
   - 检查端口是否已被其他程序使用
   - 修改配置文件中的端口设置

2. **连接超时**
   - 检查防火墙设置
   - 验证目标服务器的可达性

3. **性能问题**
   - 调整线程池大小
   - 增加 JVM 内存配置
   - 检查网络带宽限制

### 日志分析

日志文件位置: `logs/socket-relay.log`

```bash
# 查看错误日志
grep "ERROR" logs/socket-relay.log

# 查看连接日志
grep "连接" logs/socket-relay.log

# 实时监控日志
tail -f logs/socket-relay.log
```

## 开发指南

### 项目结构

```
src/main/java/com/ux/relay/
├── config/          # 配置类
├── controller/      # REST 控制器
├── core/           # 转发引擎核心
├── entity/         # 实体类
├── repository/     # 数据访问层
├── service/        # 业务逻辑层
└── SocketRelayApplication.java
```

### 🐛 BUG跟踪与修复

项目当前存在一些已知问题，我们正在积极修复中。详细信息请查看：

📋 **[BUG跟踪记录表](BUG_TRACKING.md)**

#### BUG统计概览
- 🔴 **严重BUG**: 3个 (菜单导航、WebSocket连接、页面状态)
- 🟡 **重要BUG**: 4个 (告警页面、审计日志、监控图表、内存泄漏)
- 🟢 **一般BUG**: 3个 (UI优化、配置优化、功能完善)

#### 当前修复进度
- ✅ **已修复**: 1个 (Toast提示位置)
- 🔄 **进行中**: 3个 (核心功能BUG)
- 📋 **待处理**: 6个 (功能完善类BUG)

> 💡 **贡献提示**: 欢迎提交PR修复这些BUG，请参考BUG跟踪表中的技术细节。

### 扩展开发

1. **添加新协议支持**
   - 实现新的 ChannelHandler
   - 扩展 ForwardingEngine
   - 更新前端界面

2. **自定义告警规则**
   - 扩展 AlertService
   - 添加新的告警类型
   - 实现告警通知渠道

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

## 作者

- **小白很菜** - 初始开发 - 2025-08-02

## 致谢

- [Spring Boot](https://spring.io/projects/spring-boot) - 应用框架
- [Netty](https://netty.io/) - 网络通信框架
- [Bootstrap](https://getbootstrap.com/) - 前端 UI 框架
- [Chart.js](https://www.chartjs.org/) - 图表库

---

如有问题或建议，欢迎提交 Issue 或 Pull Request！
