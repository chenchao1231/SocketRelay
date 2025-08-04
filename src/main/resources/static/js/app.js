// 全局变量
let metricsChart = null;
let performanceChart = null;
let stompClient = null;
let currentPage = 'dashboard';

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    // 首先检查session是否有效
    checkSessionAndInitialize();

    // 监听浏览器前进后退事件
    window.addEventListener('popstate', function(event) {
        if (event.state && event.state.page) {
            showPage(event.state.page, false); // false表示不更新URL
        } else {
            // 如果没有状态，从URL获取当前页面
            const currentPage = getCurrentPageFromUrl();
            showPage(currentPage, false);
        }
    });
});

// URL路由相关函数

// 从URL获取当前页面
function getCurrentPageFromUrl() {
    const hash = window.location.hash;
    if (hash && hash.startsWith('#')) {
        const page = hash.substring(1);
        // 验证页面是否有效
        const validPages = ['dashboard', 'rules', 'connections', 'metrics', 'alerts', 'ip-access', 'logs'];
        return validPages.includes(page) ? page : 'dashboard';
    }
    return 'dashboard';
}

// 更新URL而不刷新页面
function updateUrl(page) {
    const newUrl = `${window.location.pathname}#${page}`;
    window.history.pushState({ page: page }, '', newUrl);
}

// 设置页面标题
function setPageTitle(page) {
    const titles = {
        'dashboard': '仪表板 - Socket Relay管理系统',
        'rules': '转发规则 - Socket Relay管理系统',
        'connections': '连接管理 - Socket Relay管理系统',
        'metrics': '监控指标 - Socket Relay管理系统',
        'alerts': '告警中心 - Socket Relay管理系统',
        'ip-access': 'IP访问控制 - Socket Relay管理系统',
        'logs': '审计日志 - Socket Relay管理系统'
    };

    document.title = titles[page] || 'Socket Relay管理系统';
}

// 更新导航状态
function updateNavigation(pageName) {
    // 移除所有导航链接的active状态
    document.querySelectorAll('.nav-link').forEach(link => {
        link.classList.remove('active');
    });

    // 为当前页面的导航链接添加active状态
    const currentNavLink = document.querySelector(`[onclick*="showPage('${pageName}')"]`) ||
                           document.querySelector(`[data-page="${pageName}"]`);
    if (currentNavLink) {
        currentNavLink.classList.add('active');
    }
}

// 绑定导航事件
function bindNavigationEvents() {
    console.log('绑定导航事件...');

    // 为所有导航链接添加事件监听器
    document.querySelectorAll('.nav-link[onclick*="showPage"]').forEach(link => {
        const onclickAttr = link.getAttribute('onclick');
        if (onclickAttr) {
            // 提取页面名称
            const match = onclickAttr.match(/showPage\('([^']+)'\)/);
            if (match) {
                const pageName = match[1];
                link.setAttribute('data-page', pageName);

                // 移除原有的onclick属性
                link.removeAttribute('onclick');

                // 添加新的事件监听器
                link.addEventListener('click', function(e) {
                    e.preventDefault();
                    console.log('导航点击:', pageName);
                    showPage(pageName);
                });
            }
        }
    });

    console.log('导航事件绑定完成');
}

// IP访问控制相关函数

// 加载IP访问控制数据
function loadIpAccessData() {
    // 加载统计信息
    fetch('/api/ip-access/stats')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const stats = data.data;
                document.getElementById('allow-rules-count').textContent = stats.allowRules || 0;
                document.getElementById('deny-rules-count').textContent = stats.denyRules || 0;
                document.getElementById('total-rules-count').textContent = stats.totalRules || 0;
            }
        })
        .catch(error => console.error('加载IP访问控制统计失败:', error));

    // 加载规则列表
    fetch('/api/ip-access')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                updateIpRulesTable(data.data);
            }
        })
        .catch(error => console.error('加载IP访问控制规则失败:', error));
}

// 更新IP规则表格
function updateIpRulesTable(rules) {
    const tbody = document.getElementById('ip-rules-tbody');
    tbody.innerHTML = '';

    if (!rules || rules.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted">暂无IP访问控制规则</td></tr>';
        return;
    }

    rules.forEach(rule => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td><code>${rule.ipAddress}</code></td>
            <td>
                <span class="badge ${rule.accessType === 'ALLOW' ? 'bg-success' : 'bg-danger'}">
                    ${rule.accessType === 'ALLOW' ? '白名单' : '黑名单'}
                </span>
            </td>
            <td>${rule.ruleId ? `规则 ${rule.ruleId}` : '<span class="text-info">全局</span>'}</td>
            <td><span class="badge bg-secondary">${rule.priority}</span></td>
            <td>${rule.description || '-'}</td>
            <td>
                <span class="badge ${rule.enabled ? 'bg-success' : 'bg-secondary'}">
                    ${rule.enabled ? '启用' : '禁用'}
                </span>
            </td>
            <td>${formatDateTime(rule.createdAt)}</td>
            <td>
                <button class="btn btn-sm btn-outline-primary me-1" onclick="event.stopPropagation(); editIpRule(${rule.id})" title="编辑">
                    <i class="bi bi-pencil"></i>
                </button>
                <button class="btn btn-sm ${rule.enabled ? 'btn-outline-warning' : 'btn-outline-success'} me-1"
                        onclick="event.stopPropagation(); toggleIpRule(${rule.id}, ${!rule.enabled})"
                        title="${rule.enabled ? '禁用' : '启用'}">
                    <i class="bi ${rule.enabled ? 'bi-pause' : 'bi-play'}"></i>
                </button>
                <button class="btn btn-sm btn-outline-danger" onclick="event.stopPropagation(); deleteIpRule(${rule.id})" title="删除">
                    <i class="bi bi-trash"></i>
                </button>
            </td>
        `;
        tbody.appendChild(row);
    });
}

// 显示添加IP规则模态框
function showAddIpRuleModal() {
    let modal = document.getElementById('addIpRuleModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'addIpRuleModal';
        modal.className = 'modal fade';
        modal.innerHTML = `
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title">添加IP访问控制规则</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <form id="addIpRuleForm">
                            <div class="mb-3">
                                <label for="ipAddress" class="form-label">IP地址/网段</label>
                                <input type="text" class="form-control" id="ipAddress" required
                                       placeholder="例如: 192.168.1.100 或 192.168.1.0/24">
                                <div class="form-text">支持单个IP地址或CIDR网段格式</div>
                            </div>
                            <div class="mb-3">
                                <label for="accessType" class="form-label">访问类型</label>
                                <select class="form-select" id="accessType" required>
                                    <option value="ALLOW">白名单（允许访问）</option>
                                    <option value="DENY">黑名单（拒绝访问）</option>
                                </select>
                            </div>
                            <div class="mb-3">
                                <label for="ruleId" class="form-label">适用规则</label>
                                <select class="form-select" id="ruleId">
                                    <option value="">全局规则（适用于所有转发规则）</option>
                                </select>
                                <div class="form-text">选择特定转发规则或留空表示全局规则</div>
                            </div>
                            <div class="mb-3">
                                <label for="priority" class="form-label">优先级</label>
                                <input type="number" class="form-control" id="priority" value="100" min="1" max="999">
                                <div class="form-text">数字越小优先级越高</div>
                            </div>
                            <div class="mb-3">
                                <label for="description" class="form-label">描述</label>
                                <textarea class="form-control" id="description" rows="2"
                                          placeholder="可选，描述此规则的用途"></textarea>
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>
                        <button type="button" class="btn btn-primary" onclick="saveIpRule()">保存</button>
                    </div>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
    }

    // 加载转发规则选项
    loadForwardRulesForSelect();

    const bootstrapModal = new bootstrap.Modal(modal);
    bootstrapModal.show();
}

// 加载转发规则到选择框
function loadForwardRulesForSelect() {
    fetch('/api/rules')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const select = document.getElementById('ruleId');
                // 清空现有选项（保留全局选项）
                while (select.children.length > 1) {
                    select.removeChild(select.lastChild);
                }

                // 添加转发规则选项
                data.data.forEach(rule => {
                    const option = document.createElement('option');
                    option.value = rule.id;
                    option.textContent = `${rule.ruleName} (${rule.sourcePort} -> ${rule.targetIp}:${rule.targetPort})`;
                    select.appendChild(option);
                });
            }
        })
        .catch(error => console.error('加载转发规则失败:', error));
}

// 保存IP规则
function saveIpRule() {
    const ipRule = {
        ipAddress: document.getElementById('ipAddress').value,
        accessType: document.getElementById('accessType').value,
        ruleId: document.getElementById('ruleId').value || null,
        priority: parseInt(document.getElementById('priority').value),
        description: document.getElementById('description').value
    };

    fetch('/api/ip-access', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(ipRule)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showToast('IP访问控制规则添加成功', 'success');
            bootstrap.Modal.getInstance(document.getElementById('addIpRuleModal')).hide();
            loadIpAccessData();
        } else {
            showToast('添加失败: ' + data.message, 'error');
        }
    })
    .catch(error => {
        console.error('添加IP规则失败:', error);
        showToast('添加IP规则失败', 'error');
    });
}

// 显示IP测试模态框
function showIpTestModal() {
    let modal = document.getElementById('ipTestModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'ipTestModal';
        modal.className = 'modal fade';
        modal.innerHTML = `
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title">测试IP访问权限</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <form id="ipTestForm">
                            <div class="mb-3">
                                <label for="testIp" class="form-label">测试IP地址</label>
                                <input type="text" class="form-control" id="testIp" required
                                       placeholder="例如: 192.168.1.100">
                            </div>
                            <div class="mb-3">
                                <label for="testRuleId" class="form-label">测试规则</label>
                                <select class="form-select" id="testRuleId">
                                    <option value="">全局规则测试</option>
                                </select>
                            </div>
                            <div id="testResult" class="mt-3" style="display: none;">
                                <!-- 测试结果显示区域 -->
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">关闭</button>
                        <button type="button" class="btn btn-primary" onclick="testIpAccess()">测试</button>
                    </div>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
    }

    // 加载转发规则选项
    loadForwardRulesForTest();

    const bootstrapModal = new bootstrap.Modal(modal);
    bootstrapModal.show();
}

// 加载转发规则到测试选择框
function loadForwardRulesForTest() {
    fetch('/api/rules')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const select = document.getElementById('testRuleId');
                // 清空现有选项（保留全局选项）
                while (select.children.length > 1) {
                    select.removeChild(select.lastChild);
                }

                // 添加转发规则选项
                data.data.forEach(rule => {
                    const option = document.createElement('option');
                    option.value = rule.id;
                    option.textContent = `${rule.ruleName} (${rule.sourcePort})`;
                    select.appendChild(option);
                });
            }
        })
        .catch(error => console.error('加载转发规则失败:', error));
}

// 测试IP访问权限
function testIpAccess() {
    const testIp = document.getElementById('testIp').value;
    const testRuleId = document.getElementById('testRuleId').value;

    if (!testIp) {
        showToast('请输入要测试的IP地址', 'error');
        return;
    }

    const params = new URLSearchParams({
        clientIp: testIp
    });

    if (testRuleId) {
        params.append('ruleId', testRuleId);
    }

    fetch(`/api/ip-access/test?${params}`, {
        method: 'POST'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            const result = data.data;
            const resultDiv = document.getElementById('testResult');
            resultDiv.style.display = 'block';
            resultDiv.innerHTML = `
                <div class="alert ${result.allowed ? 'alert-success' : 'alert-danger'}">
                    <h6><i class="bi ${result.allowed ? 'bi-check-circle' : 'bi-x-circle'}"></i> 测试结果</h6>
                    <p><strong>IP地址:</strong> ${result.clientIp}</p>
                    <p><strong>测试规则:</strong> ${result.ruleId ? `规则 ${result.ruleId}` : '全局规则'}</p>
                    <p><strong>访问结果:</strong> ${result.message}</p>
                </div>
            `;
        } else {
            showToast('测试失败: ' + data.message, 'error');
        }
    })
    .catch(error => {
        console.error('IP访问权限测试失败:', error);
        showToast('IP访问权限测试失败', 'error');
    });
}

// 切换IP规则状态
function toggleIpRule(id, enabled) {
    fetch(`/api/ip-access/${id}/toggle?enabled=${enabled}`, {
        method: 'POST'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showToast(enabled ? 'IP规则已启用' : 'IP规则已禁用', 'success');
            loadIpAccessData();
        } else {
            showToast('操作失败: ' + data.message, 'error');
        }
    })
    .catch(error => {
        console.error('切换IP规则状态失败:', error);
        showToast('操作失败', 'error');
    });
}

// 删除IP规则
function deleteIpRule(id) {
    if (!confirm('确定要删除这个IP访问控制规则吗？')) {
        return;
    }

    fetch(`/api/ip-access/${id}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showToast('IP访问控制规则删除成功', 'success');
            loadIpAccessData();
        } else {
            showToast('删除失败: ' + data.message, 'error');
        }
    })
    .catch(error => {
        console.error('删除IP规则失败:', error);
        showToast('删除失败', 'error');
    });
}

// 显示清除历史记录模态框
function showClearHistoryModal() {
    let modal = document.getElementById('clearHistoryModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'clearHistoryModal';
        modal.className = 'modal fade';
        modal.innerHTML = `
            <div class="modal-dialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title">清除历史连接记录</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <div class="alert alert-warning">
                            <i class="bi bi-exclamation-triangle"></i>
                            <strong>注意：</strong>此操作将删除所有历史连接记录，但不会影响当前活跃的连接。
                        </div>

                        <div id="history-stats-detail" class="mb-3">
                            <h6>连接统计：</h6>
                            <div class="row">
                                <div class="col-md-4">
                                    <div class="text-center">
                                        <div class="h4 text-primary" id="modal-total-connections">-</div>
                                        <small class="text-muted">总连接数</small>
                                    </div>
                                </div>
                                <div class="col-md-4">
                                    <div class="text-center">
                                        <div class="h4 text-success" id="modal-active-connections">-</div>
                                        <small class="text-muted">活跃连接</small>
                                    </div>
                                </div>
                                <div class="col-md-4">
                                    <div class="text-center">
                                        <div class="h4 text-warning" id="modal-history-connections">-</div>
                                        <small class="text-muted">历史记录</small>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="form-check">
                            <input class="form-check-input" type="checkbox" id="confirmClearHistory">
                            <label class="form-check-label" for="confirmClearHistory">
                                我确认要清除历史连接记录（不影响活跃连接）
                            </label>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>
                        <button type="button" class="btn btn-warning" onclick="clearHistoryConnections()" id="clearHistoryBtn" disabled>
                            <i class="bi bi-trash"></i> 清除历史记录
                        </button>
                    </div>
                </div>
            </div>
        `;
        document.body.appendChild(modal);

        // 添加确认复选框事件监听
        const checkbox = modal.querySelector('#confirmClearHistory');
        const clearBtn = modal.querySelector('#clearHistoryBtn');
        checkbox.addEventListener('change', function() {
            clearBtn.disabled = !this.checked;
        });
    }

    // 加载最新统计数据
    fetch('/api/rules/connections/history-stats')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const stats = data.data;
                document.getElementById('modal-total-connections').textContent = stats.totalConnections;
                document.getElementById('modal-active-connections').textContent = stats.activeConnections;
                document.getElementById('modal-history-connections').textContent = stats.historyConnections;
            }
        })
        .catch(error => console.error('加载统计数据失败:', error));

    const bootstrapModal = new bootstrap.Modal(modal);
    bootstrapModal.show();
}

// 清除历史连接记录
function clearHistoryConnections() {
    const confirmCheckbox = document.getElementById('confirmClearHistory');
    if (!confirmCheckbox.checked) {
        showToast('请先确认清除操作', 'error');
        return;
    }

    fetch('/api/rules/connections/clear-history', {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showToast(`清除历史记录成功，删除了 ${data.data} 条记录`, 'success');
            bootstrap.Modal.getInstance(document.getElementById('clearHistoryModal')).hide();
            loadConnectionsData(); // 刷新连接数据
        } else {
            showToast('清除历史记录失败: ' + data.message, 'error');
        }
    })
    .catch(error => {
        console.error('清除历史记录失败:', error);
        showToast('清除历史记录失败', 'error');
    });
}

// 检查session并初始化应用
function checkSessionAndInitialize() {
    fetch('/api/metrics/current')
        .then(response => {
            if (response.status === 401) {
                // Session失效，跳转到登录页面
                window.location.href = '/login';
                return;
            }
            if (response.ok) {
                // Session有效，初始化应用
                initializeApp();
            } else {
                // 其他错误，也跳转到登录页面
                window.location.href = '/login';
            }
        })
        .catch(error => {
            console.error('Session检查失败:', error);
            // 网络错误，跳转到登录页面
            window.location.href = '/login';
        });
}

// 初始化应用
function initializeApp() {
    console.log('初始化Socket Relay管理系统...');

    // 初始化图表
    initializeCharts();

    // 连接WebSocket
    connectWebSocket();

    // 从URL获取当前页面并显示
    const currentPage = getCurrentPageFromUrl();
    console.log('初始化时的当前页面:', currentPage);
    showPage(currentPage, false); // false表示不更新URL，因为URL已经是正确的

    // 设置定时刷新
    setInterval(refreshCurrentPageData, 5000); // 每5秒刷新一次

    // 绑定导航事件
    bindNavigationEvents();

    console.log('系统初始化完成');
}

// 初始化图表
function initializeCharts() {
    // 初始化指标图表
    const metricsCtx = document.getElementById('metricsChart').getContext('2d');
    metricsChart = new Chart(metricsCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: '活跃连接',
                data: [],
                borderColor: 'rgb(75, 192, 192)',
                backgroundColor: 'rgba(75, 192, 192, 0.2)',
                tension: 0.1
            }, {
                label: '传输速率 (KB/s)',
                data: [],
                borderColor: 'rgb(255, 99, 132)',
                backgroundColor: 'rgba(255, 99, 132, 0.2)',
                tension: 0.1
            }]
        },
        options: {
            responsive: true,
            scales: {
                y: {
                    beginAtZero: true
                }
            }
        }
    });
    
    // 初始化性能图表
    const performanceCtx = document.getElementById('performanceChart').getContext('2d');
    performanceChart = new Chart(performanceCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'CPU使用率 (%)',
                data: [],
                borderColor: 'rgb(54, 162, 235)',
                backgroundColor: 'rgba(54, 162, 235, 0.2)',
                tension: 0.1
            }, {
                label: '内存使用率 (%)',
                data: [],
                borderColor: 'rgb(255, 206, 86)',
                backgroundColor: 'rgba(255, 206, 86, 0.2)',
                tension: 0.1
            }]
        },
        options: {
            responsive: true,
            scales: {
                y: {
                    beginAtZero: true,
                    max: 100
                }
            }
        }
    });
}

// 连接WebSocket
// 连接WebSocket
function connectWebSocket() {
    try {
        // 检查是否有Stomp库
        if (typeof StompJs === 'undefined') {
            console.warn('Stomp.js库未加载，跳过WebSocket连接');
            return;
        }

        // 如果已经有连接且处于连接状态，则不重复连接
        if (stompClient && stompClient.connected) {
            console.log('WebSocket已连接，跳过重复连接');
            return;
        }

        // 使用新版本的Stomp.js API
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/ws`;

        stompClient = new StompJs.Client({
            brokerURL: wsUrl,
            connectHeaders: {
                // 添加认证头，确保WebSocket连接可以成功通过认证
                'Authorization': 'Bearer ' + getAuthToken() // 如果使用token认证
            },
            debug: function (str) {
                console.log('STOMP: ' + str);
            },
            reconnectDelay: 5000,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
            // 确保WebSocket连接携带认证信息
            webSocketFactory: function () {
                const socket = new SockJS('/ws');
                return socket;
            }
        });

        stompClient.onConnect = function (frame) {
            console.log('WebSocket连接成功');

            // 订阅告警消息
            stompClient.subscribe('/topic/alerts', function(message) {
                try {
                    const alert = JSON.parse(message.body);
                    console.log('收到告警消息:', alert);
                    showAlert(alert);
                    showToast(alert.message || '收到新告警', alert.level || 'info');
                } catch (e) {
                    console.error('解析告警消息失败:', e);
                }
            });

            // 订阅实时指标
            stompClient.subscribe('/topic/metrics', function(message) {
                try {
                    const metrics = JSON.parse(message.body);
                    console.log('收到实时指标:', metrics);
                    // 可以在这里更新仪表板的实时数据
                } catch (e) {
                    console.error('解析指标消息失败:', e);
                }
            });

            // 订阅状态更新
            stompClient.subscribe('/topic/status', function(message) {
                try {
                    const status = JSON.parse(message.body);
                    console.log('收到状态更新:', status);
                    showToast(`${status.component}: ${status.message}`, 'info');
                } catch (e) {
                    console.error('解析状态消息失败:', e);
                }
            });
        };

        stompClient.onStompError = function (frame) {
            console.warn('STOMP连接错误，将使用后备方案');
            // 使用后备连接方案
            tryLegacyWebSocket();
        };

        stompClient.onWebSocketError = function (error) {
            console.warn('WebSocket连接失败，将使用后备方案');
            // 使用后备连接方案
            tryLegacyWebSocket();
        };

        stompClient.activate();

    } catch (error) {
        console.error('WebSocket初始化失败:', error);
        // 如果新版本API失败，尝试使用SockJS + 旧版本API作为后备
        tryLegacyWebSocket();
    }
}

// 后备的WebSocket连接方法（使用SockJS + 旧版本Stomp）
// 后备的WebSocket连接方法（使用SockJS + 旧版本Stomp）
function tryLegacyWebSocket() {
    try {
        // 检查SockJS是否可用
        if (typeof SockJS === 'undefined') {
            console.warn('SockJS库未加载，无法建立WebSocket连接');
            return;
        }

        // 避免重复连接
        if (stompClient && stompClient.connected) {
            return;
        }

        // 创建SockJS连接时确保携带认证信息
        const socket = new SockJS('/ws', null, {
            // 确保携带cookie等认证信息
            transports: ['websocket', 'xhr-streaming', 'iframe-eventsource', 'iframe-htmlfile', 'xhr-polling']
        });

        // 检查是否有全局的Stomp对象
        if (typeof Stomp !== 'undefined') {
            stompClient = Stomp.over(socket);
        } else if (typeof StompJs !== 'undefined' && StompJs.Stomp) {
            stompClient = StompJs.Stomp.over(socket);
        } else {
            console.warn('Stomp库未找到，无法建立WebSocket连接');
            return;
        }

        // 禁用调试输出
        stompClient.debug = null;

        // 添加连接头确保认证
        const headers = {
            // 如果使用token认证，需要添加token
            // 'Authorization': 'Bearer ' + getAuthToken()
        };

        stompClient.connect(headers, function(frame) {
            console.log('WebSocket连接成功 (SockJS)');

            // 订阅告警消息
            stompClient.subscribe('/topic/alerts', function(message) {
                try {
                    const alert = JSON.parse(message.body);
                    console.log('收到告警消息 (SockJS):', alert);
                    showAlert(alert);
                    showToast(alert.message || '收到新告警', alert.level || 'info');
                } catch (e) {
                    console.error('解析告警消息失败:', e);
                }
            });

            // 订阅实时指标
            stompClient.subscribe('/topic/metrics', function(message) {
                try {
                    const metrics = JSON.parse(message.body);
                    console.log('收到实时指标 (SockJS):', metrics);
                } catch (e) {
                    console.error('解析指标消息失败:', e);
                }
            });

            // 订阅状态更新
            stompClient.subscribe('/topic/status', function(message) {
                try {
                    const status = JSON.parse(message.body);
                    console.log('收到状态更新 (SockJS):', status);
                    showToast(`${status.component}: ${status.message}`, 'info');
                } catch (e) {
                    console.error('解析状态消息失败:', e);
                }
            });

        }, function(error) {
            console.warn('WebSocket连接失败，将在30秒后重试');
            // 30秒后重试连接，避免频繁重连
            setTimeout(function() {
                if (!stompClient || !stompClient.connected) {
                    connectWebSocket();
                }
            }, 30000);
        });
    } catch (error) {
        console.warn('WebSocket初始化失败，系统将在无WebSocket模式下运行:', error);
    }
}

// 显示页面
function showPage(pageName, updateUrlFlag = true) {
    console.log('showPage调用:', pageName, 'updateUrlFlag:', updateUrlFlag);

    // 隐藏所有页面
    document.querySelectorAll('.page-content').forEach(page => {
        page.style.display = 'none';
    });

    // 显示指定页面
    const targetPage = document.getElementById(pageName + '-page');
    console.log('目标页面元素:', targetPage);
    if (targetPage) {
        targetPage.style.display = 'block';
        console.log('页面已显示:', pageName);
    } else {
        console.error('找不到页面元素:', pageName + '-page');
    }

    // 更新导航状态
    updateNavigation(pageName);

    // 更新页面标题
    const titles = {
        'dashboard': '仪表板',
        'rules': '转发规则',
        'connections': '连接管理',
        'metrics': '监控指标',
        'alerts': '告警中心',
        'ip-access': 'IP访问控制',
        'logs': '审计日志'
    };

    const pageTitle = document.getElementById('page-title');
    if (pageTitle) {
        pageTitle.textContent = titles[pageName] || '未知页面';
    }

    // 设置浏览器标题
    setPageTitle(pageName);

    // 更新URL（如果需要）
    if (updateUrlFlag) {
        updateUrl(pageName);
    }

    currentPage = pageName;

    // 加载页面数据
    loadPageData(pageName);
}

// 加载页面数据
function loadPageData(pageName) {
    switch(pageName) {
        case 'dashboard':
            loadDashboardData();
            break;
        case 'rules':
            loadRulesData();
            break;
        case 'connections':
            loadConnectionsData();
            break;
        case 'metrics':
            loadMetricsData();
            break;
        case 'ip-access':
            loadIpAccessData();
            break;
        case 'alerts':
            loadAlertsData();
            break;
        case 'logs':
            loadLogsData();
            break;
    }
}

// 加载仪表板数据
function loadDashboardData() {
    // 加载当前指标
    fetch('/api/metrics/current')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                updateDashboardMetrics(data.data);
            }
        })
        .catch(error => console.error('加载仪表板数据失败:', error));
    
    // 加载最近指标用于图表
    fetch('/api/metrics/recent')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                updateMetricsChart(data.data);
            }
        })
        .catch(error => console.error('加载指标图表数据失败:', error));
}

// 更新仪表板指标
function updateDashboardMetrics(metrics) {
    document.getElementById('active-connections').textContent = metrics.activeConnections;
    document.getElementById('total-connections').textContent = metrics.totalConnections;
    document.getElementById('bytes-per-second').textContent = formatBytes(metrics.bytesPerSecond) + '/s';
    document.getElementById('error-rate').textContent = (metrics.errorRate * 100).toFixed(2) + '%';
    
    updateLastUpdateTime();
}

// 更新指标图表
function updateMetricsChart(metricsData) {
    if (!metricsChart || !metricsData || metricsData.length === 0) return;
    
    const labels = metricsData.map(m => new Date(m.timestamp).toLocaleTimeString());
    const connections = metricsData.map(m => m.activeConnections);
    const bytesPerSecond = metricsData.map(m => m.bytesPerSecond / 1024); // 转换为KB/s
    
    metricsChart.data.labels = labels;
    metricsChart.data.datasets[0].data = connections;
    metricsChart.data.datasets[1].data = bytesPerSecond;
    metricsChart.update();
}

// 加载转发规则数据
function loadRulesData() {
    // 同时加载规则和状态信息
    Promise.all([
        fetch('/api/rules').then(response => response.json()),
        fetch('/api/rules/status-overview').then(response => response.json())
    ])
    .then(([rulesData, statusData]) => {
        if (rulesData.success && statusData.success) {
            updateRulesTable(rulesData.data.content, statusData.data);
        } else {
            // 如果状态获取失败，只显示基本规则信息
            if (rulesData.success) {
                updateRulesTable(rulesData.data.content, []);
            }
        }
    })
    .catch(error => console.error('加载转发规则失败:', error));
}

// 更新转发规则表格
function updateRulesTable(rules, statusList) {
    const tbody = document.getElementById('rules-tbody');
    tbody.innerHTML = '';

    // 创建状态映射
    const statusMap = {};
    if (statusList) {
        statusList.forEach(status => {
            statusMap[status.ruleId] = status;
        });
    }

    rules.forEach(rule => {
        const status = statusMap[rule.id];
        const dataSourceName = rule.dataSourceName || `${rule.targetIp}:${rule.targetPort}`;

        const row = document.createElement('tr');
        row.style.cursor = 'pointer';
        row.onclick = () => showClientConnections(rule.id);
        row.innerHTML = `
            <td>
                <strong>${rule.ruleName}</strong>
                ${rule.remark ? `<br><small class="text-muted">${rule.remark}</small>` : ''}
            </td>
            <td><span class="badge bg-info">${rule.protocol}</span></td>
            <td><code>${rule.sourcePort}</code></td>
            <td>
                <div>${dataSourceName}</div>
                ${status && status.reconnectionAttempts > 0 ?
                    `<small class="text-warning">重连尝试: ${status.reconnectionAttempts}</small>` : ''}
            </td>
            <td>
                ${status ? getDataSourceStatusBadge(status.connectionStatus, status.activeDataSourceConnections, status.totalDataSourceConnections) :
                    '<span class="badge bg-secondary">未知</span>'}
            </td>
            <td>
                <span class="badge bg-primary">${status ? status.clientConnections : 0}</span>
            </td>
            <td>
                <span class="badge ${rule.enabled ? 'bg-success' : 'bg-secondary'}">
                    ${rule.enabled ? '启用' : '禁用'}
                </span>
            </td>
            <td>
                <div class="btn-group" role="group">
                    <button class="btn btn-sm btn-outline-primary" onclick="event.stopPropagation(); toggleRule(${rule.id}, ${!rule.enabled})"
                            title="${rule.enabled ? '禁用规则' : '启用规则'}">
                        <i class="bi bi-${rule.enabled ? 'pause' : 'play'}"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-secondary" onclick="event.stopPropagation(); editRule(${rule.id})"
                            title="编辑规则">
                        <i class="bi bi-pencil"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-info" onclick="event.stopPropagation(); showRuleDetails(${rule.id})"
                            title="查看详情">
                        <i class="bi bi-info-circle"></i>
                    </button>
                    <button class="btn btn-sm btn-outline-danger" onclick="event.stopPropagation(); deleteRule(${rule.id})"
                            title="删除规则">
                        <i class="bi bi-trash"></i>
                    </button>
                </div>
            </td>
        `;
        tbody.appendChild(row);
    });
}

// 获取数据源状态徽章
function getDataSourceStatusBadge(status, active, total) {
    let badgeClass = 'bg-secondary';
    let text = '未知';

    switch(status) {
        case 'CONNECTED':
            badgeClass = 'bg-success';
            text = `已连接 (${active}/${total})`;
            break;
        case 'DISCONNECTED':
            badgeClass = 'bg-danger';
            text = '断开连接';
            break;
        case 'CONNECTING':
            badgeClass = 'bg-warning';
            text = '连接中';
            break;
        default:
            text = status || '未知';
    }

    return `<span class="badge ${badgeClass}">${text}</span>`;
}

// 加载连接数据
function loadConnectionsData() {
    // 加载连接列表
    fetch('/api/connections/active/all')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                updateConnectionsTable(data.data);
            }
        })
        .catch(error => console.error('加载连接数据失败:', error));

    // 加载历史连接统计
    loadHistoryConnectionStats();
}

// 加载历史连接统计
function loadHistoryConnectionStats() {
    fetch('/api/rules/connections/history-stats')
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const stats = data.data;
                const statsInfo = document.getElementById('history-stats-info');
                if (statsInfo) {
                    statsInfo.innerHTML = `
                        总连接: ${stats.totalConnections} |
                        活跃: ${stats.activeConnections} |
                        历史: ${stats.historyConnections}
                    `;
                }
            }
        })
        .catch(error => {
            console.error('加载历史连接统计失败:', error);
            const statsInfo = document.getElementById('history-stats-info');
            if (statsInfo) {
                statsInfo.textContent = '统计加载失败';
            }
        });
}

// 更新连接表格
function updateConnectionsTable(connections) {
    const tbody = document.getElementById('connections-tbody');
    tbody.innerHTML = '';
    
    connections.forEach(conn => {
        const row = document.createElement('tr');
        const duration = conn.connectedAt ? calculateDuration(conn.connectedAt) : '--';
        const traffic = formatBytes(conn.bytesReceived + conn.bytesSent);
        
        row.innerHTML = `
            <td><code>${conn.connectionId.substring(0, 8)}...</code></td>
            <td><span class="badge bg-info">${conn.protocol}</span></td>
            <td>${conn.remoteAddress}:${conn.remotePort}</td>
            <td>${conn.localPort}</td>
            <td>
                <span class="badge ${getStatusBadgeClass(conn.status)}">
                    ${conn.status}
                </span>
            </td>
            <td>${duration}</td>
            <td>${traffic}</td>
        `;
        tbody.appendChild(row);
    });
}

// 加载其他页面数据
function loadMetricsData() {
    // 加载系统性能数据
    fetch('/api/metrics/system-performance')
        .then(response => response.json())
        .then(data => {
            if (data.success && performanceChart) {
                updatePerformanceChart(data.data);
            }
        })
        .catch(error => console.error('加载性能数据失败:', error));
}

function loadAlertsData() {
    // 显示告警历史的占位内容
    const alertsList = document.getElementById('alerts-list');
    alertsList.innerHTML = `
        <div class="text-center text-muted">
            <i class="bi bi-clock-history" style="font-size: 3rem;"></i>
            <p class="mt-3">告警历史功能开发中...</p>
        </div>
    `;
}

function loadLogsData() {
    // 显示审计日志的占位内容
    const logsTbody = document.getElementById('logs-tbody');
    logsTbody.innerHTML = `
        <tr>
            <td colspan="5" class="text-center text-muted">
                <i class="bi bi-journal-text" style="font-size: 2rem;"></i>
                <p class="mt-2">审计日志功能开发中...</p>
            </td>
        </tr>
    `;
}

// 更新性能图表
function updatePerformanceChart(performanceData) {
    if (!performanceChart || !performanceData) return;

    const now = new Date().toLocaleTimeString();

    // 添加新数据点
    performanceChart.data.labels.push(now);
    performanceChart.data.datasets[0].data.push(performanceData.cpuUsage);
    performanceChart.data.datasets[1].data.push(performanceData.memoryUsagePercent);

    // 保持最多20个数据点
    if (performanceChart.data.labels.length > 20) {
        performanceChart.data.labels.shift();
        performanceChart.data.datasets[0].data.shift();
        performanceChart.data.datasets[1].data.shift();
    }

    performanceChart.update();
}

// 刷新当前页面数据
function refreshCurrentPageData() {
    loadPageData(currentPage);
}

// 刷新数据
function refreshData() {
    loadPageData(currentPage);
    showToast('数据已刷新', 'success');
}

// 显示添加规则模态框
function showAddRuleModal() {
    const modal = new bootstrap.Modal(document.getElementById('addRuleModal'));
    modal.show();
}

// 添加规则
function addRule() {
    const form = document.getElementById('addRuleForm');

    const rule = {
        ruleName: document.getElementById('ruleName').value,
        protocol: document.getElementById('protocol').value,
        sourcePort: parseInt(document.getElementById('sourcePort').value),
        targetIp: document.getElementById('targetIp').value,
        targetPort: parseInt(document.getElementById('targetPort').value),
        dataSourceName: document.getElementById('dataSourceName').value,
        remark: document.getElementById('remark').value,
        autoReconnect: document.getElementById('autoReconnect').value === 'true',
        reconnectInterval: parseInt(document.getElementById('reconnectInterval').value) * 1000, // 转换为毫秒
        maxReconnectAttempts: parseInt(document.getElementById('maxReconnectAttempts').value),
        connectionPoolSize: parseInt(document.getElementById('connectionPoolSize').value),
        enabled: true
    };

    fetch('/api/rules', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(rule)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showToast('规则添加成功', 'success');
            bootstrap.Modal.getInstance(document.getElementById('addRuleModal')).hide();
            form.reset();
            loadRulesData();
        } else {
            showToast('添加失败: ' + data.message, 'error');
        }
    })
    .catch(error => {
        console.error('添加规则失败:', error);
        showToast('添加规则失败', 'error');
    });
}

// 切换规则状态
function toggleRule(ruleId, enable) {
    const action = enable ? 'enable' : 'disable';

    fetch(`/api/rules/${ruleId}/${action}`, {
        method: 'POST'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showToast(`规则${enable ? '启用' : '禁用'}成功`, 'success');
            loadRulesData();
        } else {
            showToast(`操作失败: ${data.message}`, 'error');
        }
    })
    .catch(error => {
        console.error('切换规则状态失败:', error);
        showToast('操作失败', 'error');
    });
}

// 删除规则
function deleteRule(ruleId) {
    if (!confirm('确定要删除这个规则吗？')) {
        return;
    }

    fetch(`/api/rules/${ruleId}`, {
        method: 'DELETE'
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showToast('规则删除成功', 'success');
            loadRulesData();
        } else {
            showToast(`删除失败: ${data.message}`, 'error');
        }
    })
    .catch(error => {
        console.error('删除规则失败:', error);
        showToast('删除失败', 'error');
    });
}

// 编辑规则
function editRule(ruleId) {
    // 先获取规则信息
    fetch(`/api/rules/${ruleId}`)
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const rule = data.data;
                showEditRuleModal(rule);
            } else {
                showToast('获取规则信息失败: ' + data.message, 'error');
            }
        })
        .catch(error => {
            console.error('获取规则信息失败:', error);
            showToast('获取规则信息失败', 'error');
        });
}

// 显示编辑规则模态框
function showEditRuleModal(rule) {
    // 创建编辑模态框
    let modal = document.getElementById('editRuleModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'editRuleModal';
        modal.className = 'modal fade';
        modal.innerHTML = `
            <div class="modal-dialog modal-lg">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title">编辑转发规则</h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body">
                        <form id="editRuleForm">
                            <div class="row">
                                <div class="col-md-8">
                                    <div class="mb-3">
                                        <label for="editRuleName" class="form-label">规则名称</label>
                                        <input type="text" class="form-control" id="editRuleName" required>
                                    </div>

                                    <!-- 核心配置区域 -->
                                    <div id="coreConfigSection">
                                        <div class="mb-3">
                                            <label for="editProtocol" class="form-label">协议类型</label>
                                            <select class="form-select" id="editProtocol" required>
                                                <option value="TCP">TCP</option>
                                                <option value="UDP">UDP</option>
                                                <option value="TCP_UDP">TCP+UDP</option>
                                            </select>
                                        </div>
                                        <div class="row">
                                            <div class="col-md-6">
                                                <div class="mb-3">
                                                    <label for="editSourcePort" class="form-label">源端口</label>
                                                    <input type="number" class="form-control" id="editSourcePort" min="1" max="65535" required>
                                                </div>
                                            </div>
                                            <div class="col-md-6">
                                                <div class="mb-3">
                                                    <label for="editTargetPort" class="form-label">目标端口</label>
                                                    <input type="number" class="form-control" id="editTargetPort" min="1" max="65535" required>
                                                </div>
                                            </div>
                                        </div>
                                        <div class="mb-3">
                                            <label for="editTargetIp" class="form-label">目标IP</label>
                                            <input type="text" class="form-control" id="editTargetIp" required>
                                        </div>
                                    </div>

                                    <div class="mb-3">
                                        <label for="editDataSourceName" class="form-label">数据源连接地址名称</label>
                                        <input type="text" class="form-control" id="editDataSourceName" placeholder="可选，用于显示友好的名称">
                                    </div>
                                    <div class="mb-3">
                                        <label for="editRemark" class="form-label">备注</label>
                                        <textarea class="form-control" id="editRemark" rows="2"></textarea>
                                    </div>
                                </div>
                                <div class="col-md-4">
                                    <h6>高级配置</h6>
                                    <div class="mb-3">
                                        <label for="editAutoReconnect" class="form-label">自动重连</label>
                                        <select class="form-select" id="editAutoReconnect">
                                            <option value="true">启用</option>
                                            <option value="false">禁用</option>
                                        </select>
                                    </div>
                                    <div class="mb-3">
                                        <label for="editReconnectInterval" class="form-label">重连间隔(秒)</label>
                                        <input type="number" class="form-control" id="editReconnectInterval" min="1" max="300">
                                    </div>
                                    <div class="mb-3">
                                        <label for="editMaxReconnectAttempts" class="form-label">最大重连次数</label>
                                        <input type="number" class="form-control" id="editMaxReconnectAttempts" min="1" max="100">
                                    </div>
                                    <div class="mb-3">
                                        <label for="editConnectionPoolSize" class="form-label">连接池大小</label>
                                        <input type="number" class="form-control" id="editConnectionPoolSize" min="1" max="10">
                                    </div>
                                </div>
                            </div>

                            <div class="alert alert-warning" id="coreConfigWarning" style="display: none;">
                                <i class="bi bi-exclamation-triangle"></i>
                                <strong>注意：</strong>修改核心配置（协议、端口、IP）需要先停止规则。规则停止后才能修改这些配置。
                            </div>
                        </form>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">取消</button>
                        <button type="button" class="btn btn-primary" onclick="saveEditRule()">保存</button>
                    </div>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
    }

    // 填充表单数据
    document.getElementById('editRuleName').value = rule.ruleName || '';
    document.getElementById('editProtocol').value = rule.protocol || 'TCP';
    document.getElementById('editSourcePort').value = rule.sourcePort || '';
    document.getElementById('editTargetIp').value = rule.targetIp || '';
    document.getElementById('editTargetPort').value = rule.targetPort || '';
    document.getElementById('editDataSourceName').value = rule.dataSourceName || '';
    document.getElementById('editRemark').value = rule.remark || '';
    document.getElementById('editAutoReconnect').value = rule.autoReconnect ? 'true' : 'false';
    document.getElementById('editReconnectInterval').value = (rule.reconnectInterval || 5000) / 1000;
    document.getElementById('editMaxReconnectAttempts').value = rule.maxReconnectAttempts || 10;
    document.getElementById('editConnectionPoolSize').value = rule.connectionPoolSize || 1;

    // 根据规则状态控制核心配置是否可编辑
    const coreConfigInputs = ['editProtocol', 'editSourcePort', 'editTargetIp', 'editTargetPort'];
    const coreConfigWarning = document.getElementById('coreConfigWarning');

    if (rule.enabled) {
        // 规则启用时，禁用核心配置编辑
        coreConfigInputs.forEach(id => {
            document.getElementById(id).disabled = true;
        });
        coreConfigWarning.style.display = 'block';
    } else {
        // 规则停止时，允许核心配置编辑
        coreConfigInputs.forEach(id => {
            document.getElementById(id).disabled = false;
        });
        coreConfigWarning.style.display = 'none';
    }

    // 保存规则ID和状态到模态框
    modal.setAttribute('data-rule-id', rule.id);
    modal.setAttribute('data-rule-enabled', rule.enabled);

    const bootstrapModal = new bootstrap.Modal(modal);
    bootstrapModal.show();
}

// 保存编辑的规则
function saveEditRule() {
    const modal = document.getElementById('editRuleModal');
    const ruleId = modal.getAttribute('data-rule-id');
    const ruleEnabled = modal.getAttribute('data-rule-enabled') === 'true';

    const updatedRule = {
        ruleName: document.getElementById('editRuleName').value,
        protocol: document.getElementById('editProtocol').value,
        sourcePort: parseInt(document.getElementById('editSourcePort').value),
        targetIp: document.getElementById('editTargetIp').value,
        targetPort: parseInt(document.getElementById('editTargetPort').value),
        dataSourceName: document.getElementById('editDataSourceName').value,
        remark: document.getElementById('editRemark').value,
        autoReconnect: document.getElementById('editAutoReconnect').value === 'true',
        reconnectInterval: parseInt(document.getElementById('editReconnectInterval').value) * 1000,
        maxReconnectAttempts: parseInt(document.getElementById('editMaxReconnectAttempts').value),
        connectionPoolSize: parseInt(document.getElementById('editConnectionPoolSize').value)
    };

    // 根据规则状态选择不同的API端点
    const apiEndpoint = ruleEnabled ? `/api/rules/${ruleId}/edit` : `/api/rules/${ruleId}/edit-core`;

    fetch(apiEndpoint, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(updatedRule)
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showToast('规则编辑成功', 'success');
            bootstrap.Modal.getInstance(modal).hide();
            loadRulesData();
        } else {
            showToast('编辑失败: ' + data.message, 'error');
        }
    })
    .catch(error => {
        console.error('编辑规则失败:', error);
        showToast('编辑规则失败', 'error');
    });
}

// 显示客户端连接详情
function showClientConnections(ruleId) {
    fetch(`/api/rules/${ruleId}/client-connections`)
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const details = data.data;
                showClientConnectionsModal(details);
            } else {
                showToast('获取客户端连接详情失败: ' + data.message, 'error');
            }
        })
        .catch(error => {
            console.error('获取客户端连接详情失败:', error);
            showToast('获取客户端连接详情失败', 'error');
        });
}

// 显示客户端连接详情模态框
function showClientConnectionsModal(details) {
    const rule = details.rule;
    const clientStats = details.clientStats;
    const poolStatus = details.poolStatus;
    const listenerStatuses = details.listenerStatuses;
    const activeConnections = details.activeConnections;

    let modalContent = `
        <div class="row">
            <div class="col-md-12">
                <h6 class="text-primary">
                    <i class="bi bi-router"></i> 规则信息: ${rule.ruleName}
                </h6>
                <div class="card mb-3">
                    <div class="card-body">
                        <div class="row">
                            <div class="col-md-6">
                                <table class="table table-sm table-borderless">
                                    <tr><td><strong>协议类型:</strong></td><td><span class="badge bg-info">${rule.protocol}</span></td></tr>
                                    <tr><td><strong>监听端口:</strong></td><td><code>${rule.sourcePort}</code></td></tr>
                                    <tr><td><strong>数据源地址:</strong></td><td><code>${rule.targetIp}:${rule.targetPort}</code></td></tr>
                                    <tr><td><strong>数据源名称:</strong></td><td>${rule.dataSourceName || '未设置'}</td></tr>
                                </table>
                            </div>
                            <div class="col-md-6">
                                <table class="table table-sm table-borderless">
                                    <tr><td><strong>规则状态:</strong></td><td><span class="badge ${rule.enabled ? 'bg-success' : 'bg-secondary'}">${rule.enabled ? '启用' : '禁用'}</span></td></tr>
                                    <tr><td><strong>数据源状态:</strong></td><td>${poolStatus ? getDataSourceStatusBadge(poolStatus.status, poolStatus.activeConnections, poolStatus.totalConnections) : '<span class="badge bg-secondary">未知</span>'}</td></tr>
                                    <tr><td><strong>客户端连接数:</strong></td><td><span class="badge bg-primary">${clientStats ? clientStats.connectionCount : 0}</span></td></tr>
                                    <tr><td><strong>重连尝试:</strong></td><td>${poolStatus ? poolStatus.reconnectionAttempts : 0} 次</td></tr>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="row">
            <div class="col-md-6">
                <h6 class="text-success">
                    <i class="bi bi-people"></i> 客户端连接统计
                </h6>
                <div class="card">
                    <div class="card-body">
                        ${clientStats ? `
                            <table class="table table-sm">
                                <tr><td>当前连接数:</td><td><span class="badge bg-primary">${clientStats.connectionCount}</span></td></tr>
                                <tr><td>接收数据:</td><td>${formatBytes(clientStats.totalReceivedBytes)}</td></tr>
                                <tr><td>发送数据:</td><td>${formatBytes(clientStats.totalSentBytes)}</td></tr>
                                <tr><td>接收包数:</td><td>${clientStats.totalReceivedPackets.toLocaleString()}</td></tr>
                                <tr><td>发送包数:</td><td>${clientStats.totalSentPackets.toLocaleString()}</td></tr>
                                <tr><td>缓存数据:</td><td>${formatBytes(clientStats.cachedDataSize)}</td></tr>
                            </table>
                        ` : '<p class="text-muted">暂无统计数据</p>'}
                    </div>
                </div>
            </div>

            <div class="col-md-6">
                <h6 class="text-info">
                    <i class="bi bi-broadcast"></i> 监听状态
                </h6>
                <div class="card">
                    <div class="card-body">
                        ${listenerStatuses && listenerStatuses.length > 0 ?
                            listenerStatuses.map(status => `
                                <div class="mb-2">
                                    <div class="d-flex justify-content-between align-items-center">
                                        <span><strong>${status.protocol}</strong> 端口 ${status.listenPort}</span>
                                        <span class="badge ${getListenerStatusBadge(status.status)}">${getListenerStatusText(status.status)}</span>
                                    </div>
                                    <small class="text-muted">
                                        当前客户端: ${status.currentClientCount} |
                                        总连接数: ${status.totalClientCount}
                                        ${status.waitingSince ? ` | 等待时间: ${formatDuration(status.waitingSince)}` : ''}
                                    </small>
                                </div>
                            `).join('') :
                            '<p class="text-muted">暂无监听状态</p>'
                        }
                    </div>
                </div>
            </div>
        </div>

        <div class="row mt-3">
            <div class="col-md-12">
                <h6 class="text-warning">
                    <i class="bi bi-list-ul"></i> 活跃客户端连接详情
                </h6>
                <div class="card">
                    <div class="card-body">
                        ${activeConnections && activeConnections.length > 0 ? `
                            <div class="table-responsive">
                                <table class="table table-sm table-hover">
                                    <thead class="table-light">
                                        <tr>
                                            <th>连接ID</th>
                                            <th>客户端地址</th>
                                            <th>连接时间</th>
                                            <th>持续时间</th>
                                            <th>接收/发送</th>
                                            <th>状态</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        ${activeConnections.map(conn => `
                                            <tr>
                                                <td><code>${(conn.connectionId || '').substring(0, 8)}...</code></td>
                                                <td><code>${conn.remoteAddress || 'N/A'}:${conn.remotePort || 'N/A'}</code></td>
                                                <td>${formatDateTime(conn.connectedAt)}</td>
                                                <td>${formatDuration(conn.connectedAt)}</td>
                                                <td>
                                                    <small>
                                                        ↓ ${formatBytes(conn.bytesReceived || 0)} (${conn.packetsReceived || 0})<br>
                                                        ↑ ${formatBytes(conn.bytesSent || 0)} (${conn.packetsSent || 0})
                                                    </small>
                                                </td>
                                                <td><span class="badge bg-success">活跃</span></td>
                                            </tr>
                                        `).join('')}
                                    </tbody>
                                </table>
                            </div>
                        ` : '<p class="text-muted text-center py-3">当前没有活跃的客户端连接</p>'}
                    </div>
                </div>
            </div>
        </div>
    `;

    showModal(`客户端连接详情 - ${rule.ruleName}`, modalContent, 'modal-xl');
}

// 获取监听状态徽章样式
function getListenerStatusBadge(status) {
    switch(status) {
        case 'ACTIVE': return 'bg-success';
        case 'WAITING_CLIENT': return 'bg-warning';
        case 'STARTING': return 'bg-info';
        case 'STOPPED': return 'bg-secondary';
        case 'ERROR': return 'bg-danger';
        default: return 'bg-secondary';
    }
}

// 获取监听状态文本
function getListenerStatusText(status) {
    switch(status) {
        case 'ACTIVE': return '活跃';
        case 'WAITING_CLIENT': return '等待客户端';
        case 'STARTING': return '启动中';
        case 'STOPPED': return '已停止';
        case 'ERROR': return '错误';
        default: return status;
    }
}

// 格式化日期时间
function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return '-';
    const date = new Date(dateTimeStr);
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
}

// 格式化持续时间
function formatDuration(startTimeStr) {
    if (!startTimeStr) return '-';
    const startTime = new Date(startTimeStr);
    const now = new Date();
    const diffMs = now - startTime;

    const seconds = Math.floor(diffMs / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) return `${days}天 ${hours % 24}小时`;
    if (hours > 0) return `${hours}小时 ${minutes % 60}分钟`;
    if (minutes > 0) return `${minutes}分钟 ${seconds % 60}秒`;
    return `${seconds}秒`;
}

// 显示规则详情
function showRuleDetails(ruleId) {
    fetch(`/api/rules/${ruleId}/status`)
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const status = data.data;
                const rule = status.rule;
                const poolStatus = status.poolStatus;
                const clientStats = status.clientStats;

                let detailsHtml = `
                    <div class="row">
                        <div class="col-md-6">
                            <h6>规则信息</h6>
                            <table class="table table-sm">
                                <tr><td>规则名称:</td><td>${rule.ruleName}</td></tr>
                                <tr><td>协议类型:</td><td>${rule.protocol}</td></tr>
                                <tr><td>源端口:</td><td>${rule.sourcePort}</td></tr>
                                <tr><td>目标地址:</td><td>${rule.targetIp}:${rule.targetPort}</td></tr>
                                <tr><td>数据源名称:</td><td>${rule.dataSourceName || '未设置'}</td></tr>
                                <tr><td>自动重连:</td><td>${rule.autoReconnect ? '启用' : '禁用'}</td></tr>
                                <tr><td>重连间隔:</td><td>${rule.reconnectInterval / 1000}秒</td></tr>
                                <tr><td>最大重连次数:</td><td>${rule.maxReconnectAttempts}</td></tr>
                                <tr><td>连接池大小:</td><td>${rule.connectionPoolSize}</td></tr>
                            </table>
                        </div>
                        <div class="col-md-6">
                            <h6>连接状态</h6>
                            <table class="table table-sm">
                `;

                if (poolStatus) {
                    detailsHtml += `
                                <tr><td>数据源状态:</td><td><span class="badge ${poolStatus.status === 'CONNECTED' ? 'bg-success' : 'bg-danger'}">${poolStatus.status}</span></td></tr>
                                <tr><td>活跃连接:</td><td>${poolStatus.activeConnections}/${poolStatus.totalConnections}</td></tr>
                                <tr><td>重连尝试:</td><td>${poolStatus.reconnectionAttempts}</td></tr>
                    `;
                } else {
                    detailsHtml += `
                                <tr><td colspan="2">数据源连接信息不可用</td></tr>
                    `;
                }

                if (clientStats) {
                    detailsHtml += `
                                <tr><td>客户端连接数:</td><td>${clientStats.connectionCount}</td></tr>
                                <tr><td>接收字节数:</td><td>${formatBytes(clientStats.totalReceivedBytes)}</td></tr>
                                <tr><td>发送字节数:</td><td>${formatBytes(clientStats.totalSentBytes)}</td></tr>
                                <tr><td>接收包数:</td><td>${clientStats.totalReceivedPackets}</td></tr>
                                <tr><td>发送包数:</td><td>${clientStats.totalSentPackets}</td></tr>
                                <tr><td>缓存数据:</td><td>${formatBytes(clientStats.cachedDataSize)}</td></tr>
                    `;
                }

                detailsHtml += `
                            </table>
                        </div>
                    </div>
                `;

                // 显示模态框
                showModal('规则详情 - ' + rule.ruleName, detailsHtml);

            } else {
                showToast('获取规则详情失败: ' + data.message, 'error');
            }
        })
        .catch(error => {
            console.error('获取规则详情失败:', error);
            showToast('获取规则详情失败', 'error');
        });
}

// 显示模态框
function showModal(title, content, size = 'modal-lg') {
    // 创建模态框（如果不存在）
    let modal = document.getElementById('dynamicModal');
    if (!modal) {
        modal = document.createElement('div');
        modal.id = 'dynamicModal';
        modal.className = 'modal fade';
        modal.innerHTML = `
            <div class="modal-dialog" id="dynamicModalDialog">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title" id="dynamicModalTitle"></h5>
                        <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
                    </div>
                    <div class="modal-body" id="dynamicModalBody">
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">关闭</button>
                    </div>
                </div>
            </div>
        `;
        document.body.appendChild(modal);
    }

    // 更新模态框大小
    const modalDialog = document.getElementById('dynamicModalDialog');
    modalDialog.className = `modal-dialog ${size}`;

    document.getElementById('dynamicModalTitle').textContent = title;
    document.getElementById('dynamicModalBody').innerHTML = content;

    const bootstrapModal = new bootstrap.Modal(modal);
    bootstrapModal.show();
}

// 显示告警
function showAlert(alert) {
    const alertsContainer = document.getElementById('recent-alerts');

    // 如果是第一个告警，清空"暂无告警"提示
    if (alertsContainer.querySelector('.text-muted')) {
        alertsContainer.innerHTML = '';
    }

    const alertElement = document.createElement('div');
    alertElement.className = `alert-item alert alert-${getLevelClass(alert.level)} alert-dismissible fade show`;
    alertElement.innerHTML = `
        <strong>${alert.title}</strong><br>
        <small>${alert.message}</small>
        <small class="text-muted d-block">${new Date(alert.timestamp).toLocaleString()}</small>
        <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
    `;

    // 插入到顶部
    alertsContainer.insertBefore(alertElement, alertsContainer.firstChild);

    // 限制显示的告警数量
    const alerts = alertsContainer.querySelectorAll('.alert-item');
    if (alerts.length > 5) {
        alerts[alerts.length - 1].remove();
    }
}

// 显示Toast消息
function showToast(message, type) {
    type = type || 'info';

    // 创建toast容器（如果不存在）
    let toastContainer = document.getElementById('toast-container');
    if (!toastContainer) {
        toastContainer = document.createElement('div');
        toastContainer.id = 'toast-container';
        toastContainer.className = 'toast-container position-fixed bottom-0 end-0 p-3';
        toastContainer.style.zIndex = '9999';
        document.body.appendChild(toastContainer);
    }

    const toastElement = document.createElement('div');
    toastElement.className = `toast align-items-center text-white bg-${type === 'error' ? 'danger' : type === 'success' ? 'success' : 'primary'} border-0`;
    toastElement.setAttribute('role', 'alert');
    toastElement.innerHTML = `
        <div class="d-flex">
            <div class="toast-body">${message}</div>
            <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
        </div>
    `;

    toastContainer.appendChild(toastElement);

    const toast = new bootstrap.Toast(toastElement);
    toast.show();

    // 3秒后自动移除
    setTimeout(() => {
        toastElement.remove();
    }, 3000);
}

// 工具函数
function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

function calculateDuration(startTime) {
    const start = new Date(startTime);
    const now = new Date();
    const diff = now - start;

    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((diff % (1000 * 60)) / 1000);

    if (hours > 0) {
        return `${hours}h ${minutes}m`;
    } else if (minutes > 0) {
        return `${minutes}m ${seconds}s`;
    } else {
        return `${seconds}s`;
    }
}

function getStatusBadgeClass(status) {
    switch(status) {
        case 'CONNECTED': return 'bg-success';
        case 'CONNECTING': return 'bg-warning';
        case 'DISCONNECTED': return 'bg-secondary';
        case 'ERROR': return 'bg-danger';
        case 'TIMEOUT': return 'bg-warning';
        default: return 'bg-secondary';
    }
}

function getLevelClass(level) {
    switch(level) {
        case 'INFO': return 'info';
        case 'WARNING': return 'warning';
        case 'CRITICAL': return 'danger';
        default: return 'info';
    }
}

function updateLastUpdateTime() {
    document.getElementById('last-update').textContent = new Date().toLocaleTimeString();
}

// 登出
function logout() {
    if (confirm('确定要退出系统吗？')) {
        fetch('/api/auth/logout', {
            method: 'POST'
        })
        .then(() => {
            window.location.href = '/login';
        })
        .catch(error => {
            console.error('登出失败:', error);
            window.location.href = '/login';
        });
    }
}

// 加载告警数据
function loadAlertsData() {
    console.log('加载告警数据...');
    // TODO: 实现告警数据加载逻辑
    // 这里可以添加获取告警数据的API调用
}

// 加载日志数据
function loadLogsData() {
    console.log('加载审计日志数据...');
    // TODO: 实现审计日志数据加载逻辑
    // 这里可以添加获取审计日志的API调用
}

// WebSocket测试函数
function testWebSocketAlert() {
    fetch('/api/websocket/test-alert', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: 'message=这是一个测试告警消息&level=warning'
    })
    .then(response => response.json())
    .then(data => {
        console.log('测试告警推送结果:', data);
        if (data.success) {
            showToast('测试告警已发送，请查看控制台和告警区域', 'success');
        }
    })
    .catch(error => {
        console.error('测试告警推送失败:', error);
        showToast('测试告警推送失败', 'error');
    });
}

function testWebSocketMetrics() {
    fetch('/api/websocket/test-metrics', {
        method: 'POST'
    })
    .then(response => response.json())
    .then(data => {
        console.log('测试指标推送结果:', data);
        if (data.success) {
            showToast('测试指标已发送，请查看控制台', 'success');
        }
    })
    .catch(error => {
        console.error('测试指标推送失败:', error);
        showToast('测试指标推送失败', 'error');
    });
}

function testWebSocketStatus() {
    fetch('/api/websocket/test-status', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: 'component=测试组件&status=正常&message=这是一个测试状态更新消息'
    })
    .then(response => response.json())
    .then(data => {
        console.log('测试状态推送结果:', data);
        if (data.success) {
            showToast('测试状态更新已发送', 'success');
        }
    })
    .catch(error => {
        console.error('测试状态推送失败:', error);
        showToast('测试状态推送失败', 'error');
    });
}
