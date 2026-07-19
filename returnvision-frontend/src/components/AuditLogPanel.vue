<template>
  <!-- 步骤1：审计日志页面外壳 -->
  <div class="screen-shell">
    <!-- 步骤2：Hero 区 -->
    <section class="hero">
      <div class="eyebrow">审计</div>
      <h1 class="hero-title">操作审计日志</h1>
      <p class="hero-subtitle">所有敏感操作可追溯 · 谁在什么时候做了什么</p>
    </section>

    <!-- 步骤3：筛选+日志卡片 -->
    <section class="card">
      <div class="card-header">
        <div class="card-heading">
          <div class="eyebrow">数据</div>
          <h3 class="card-title">操作日志</h3>
        </div>
        <div class="toolbar">
          <select v-model="filterAction" class="filter-select" @change="onFilterChange">
            <option value="">全部操作</option>
            <option v-for="opt in actionOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
          <input v-model="filterUserId" type="text" class="filter-input" placeholder="用户ID筛选" @change="onFilterChange" />
          <button class="icon-btn" :disabled="loading" @click="loadLogs">
            <Refresh />
          </button>
        </div>
      </div>

      <!-- 步骤4：日志表格 -->
      <div v-loading="loading" class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>操作者</th>
              <th>操作类型</th>
              <th>对象类型</th>
              <th>对象ID</th>
              <th>描述</th>
              <th>结果</th>
              <th>IP</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id">
              <td>{{ log.id }}</td>
              <td>
                <div>{{ log.username || '未登录' }}</div>
                <div class="sub-text">ID: {{ log.user_id || '-' }}</div>
              </td>
              <td>
                <span :class="['action-tag', `action-${log.action.toLowerCase()}`]">{{ log.action }}</span>
              </td>
              <td>{{ log.target_type || '-' }}</td>
              <td>{{ log.target_id || '-' }}</td>
              <td>{{ log.description || '-' }}</td>
              <td>
                <span :class="['status-tag', log.success ? 'status-success' : 'status-fail']">
                  {{ log.success ? '成功' : '失败' }}
                </span>
              </td>
              <td>{{ log.ip || '-' }}</td>
              <td>{{ formatTime(log.created_at) }}</td>
            </tr>
            <tr v-if="logs.length === 0">
              <td colspan="9" class="empty-row">暂无日志记录</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 步骤5：分页 -->
      <div class="pagination">
        <button class="page-btn" :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
        <span class="page-info">第 {{ page }} 页 / 共 {{ totalPages }} 页（总 {{ total }} 条）</span>
        <button class="page-btn" :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>

<script setup>
// 步骤6：组件状态与业务逻辑
import { ref, computed, onMounted } from 'vue';
import { Refresh } from '../icons';
import api from '../api';

const logs = ref([]);
const loading = ref(false);
const page = ref(1);
const size = ref(20);
const total = ref(0);

// 筛选条件
const filterAction = ref('');
const filterUserId = ref('');

const totalPages = computed(() => Math.ceil(total.value / size.value) || 1);

// 操作类型选项
const actionOptions = [
  { value: 'LOGIN', label: '登录' },
  { value: 'LOGOUT', label: '登出' },
  { value: 'FEISHU_LOGIN', label: '飞书登录' },
  { value: 'UPLOAD', label: '上传识别' },
  { value: 'CONFIRM', label: '确认写飞书' },
  { value: 'BATCH_CONFIRM', label: '批量确认' },
  { value: 'DELETE_RECORD', label: '删除记录' },
  { value: 'BATCH_DELETE_RECORD', label: '批量删除' },
  { value: 'CREATE_USER', label: '创建用户' },
  { value: 'UPDATE_USER', label: '编辑用户' },
  { value: 'DELETE_USER', label: '删除用户' },
  { value: 'RESET_PASSWORD', label: '重置密码' },
  { value: 'CHANGE_PASSWORD', label: '修改密码' },
  { value: 'UPDATE_PROFILE', label: '修改显示名' },
];

const formatTime = (t) => {
  if (!t) return '-';
  return new Date(t).toLocaleString('zh-CN');
};

/**
 * 步骤7：加载审计日志
 */
const loadLogs = async () => {
  loading.value = true;
  try {
    const resp = await api.queryAuditLogs({
      page: page.value,
      size: size.value,
      action: filterAction.value || undefined,
      userId: filterUserId.value || undefined,
    });
    logs.value = resp.data.data.logs;
    total.value = resp.data.data.total;
  } catch (err) {
    console.error('加载审计日志失败', err);
  } finally {
    loading.value = false;
  }
};

const onFilterChange = () => {
  page.value = 1;
  loadLogs();
};

const changePage = (newPage) => {
  if (newPage < 1 || newPage > totalPages.value) return;
  page.value = newPage;
  loadLogs();
};

onMounted(() => {
  loadLogs();
});
</script>

<style scoped>
.screen-shell {
  padding: 24px 32px;
  max-width: 1400px;
  margin: 0 auto;
}

.hero {
  margin-bottom: 24px;
}

.eyebrow {
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-primary, #14b8a6);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 8px;
}

.hero-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 4px 0;
  color: var(--text-primary, #111827);
}

.hero-subtitle {
  font-size: 14px;
  color: var(--text-secondary, #6b7280);
  margin: 0;
}

.card {
  background: var(--bg-card, #fff);
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 12px;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color, #e5e7eb);
}

.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.filter-select, .filter-input {
  padding: 6px 10px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 6px;
  font-size: 13px;
  background: var(--bg-card, #fff);
  color: var(--text-primary, #111827);
}

.filter-input {
  width: 120px;
}

.icon-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  cursor: pointer;
  color: var(--text-secondary, #6b7280);
}

.icon-btn:hover:not(:disabled) {
  background: var(--bg-hover, #f3f4f6);
}

.icon-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.table-wrap {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table th {
  text-align: left;
  padding: 12px 16px;
  background: var(--bg-secondary, #f9fafb);
  color: var(--text-secondary, #6b7280);
  font-weight: 500;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  white-space: nowrap;
}

.data-table td {
  padding: 12px 16px;
  border-top: 1px solid var(--border-color, #e5e7eb);
  color: var(--text-primary, #111827);
  vertical-align: top;
}

.sub-text {
  font-size: 11px;
  color: var(--text-tertiary, #9ca3af);
}

.action-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
  white-space: nowrap;
}

.status-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.status-success {
  background: rgba(34, 197, 94, 0.1);
  color: #22c55e;
}

.status-fail {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.empty-row {
  text-align: center;
  color: var(--text-tertiary, #9ca3af);
  padding: 40px 16px;
}

.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border-top: 1px solid var(--border-color, #e5e7eb);
}

.page-btn {
  padding: 6px 14px;
  background: transparent;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  color: var(--text-primary, #111827);
}

.page-btn:hover:not(:disabled) {
  background: var(--bg-hover, #f3f4f6);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-info {
  font-size: 13px;
  color: var(--text-secondary, #6b7280);
}
</style>
