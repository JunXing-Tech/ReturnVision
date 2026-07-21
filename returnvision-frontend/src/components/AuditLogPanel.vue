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
              <td class="mono">{{ log.id }}</td>
              <td>
                <div>{{ log.username || '未登录' }}</div>
                <div class="sub-text">ID: {{ log.user_id || '-' }}</div>
              </td>
              <td>
                <span :class="['action-tag', `action-${log.action.toLowerCase()}`]">{{ log.action }}</span>
              </td>
              <td>{{ log.target_type || '-' }}</td>
              <td class="mono">{{ log.target_id || '-' }}</td>
              <td>{{ log.description || '-' }}</td>
              <td>
                <span :class="['status-tag', log.success ? 'status-success' : 'status-fail']">
                  {{ log.success ? '成功' : '失败' }}
                </span>
              </td>
              <td class="mono">{{ log.ip || '-' }}</td>
              <td class="mono">{{ formatTime(log.created_at) }}</td>
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
/* 步骤15：审计日志页面样式（源力设计系统 v3.0） */
.screen-shell {
  padding: calc(var(--spacing) * 5);
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: calc(var(--spacing) * 4);
}

/* Hero 区 */
.hero { display: flex; flex-direction: column; gap: 4px; }
.eyebrow {
  font-family: var(--font-mono);
  font-size: 10.5px;
  font-weight: 500;
  color: var(--color-fg-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 4px;
}
.hero-title { font-size: 22px; font-weight: 600; color: var(--color-fg); margin: 0; }
.hero-subtitle { font-size: 13px; color: var(--color-fg-muted); margin: 0; }

/* 卡片 */
.card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow-md);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
  flex-wrap: wrap;
  gap: 12px;
}
.card-heading { display: flex; flex-direction: column; gap: 2px; }
.card-title { font-size: 14px; font-weight: 600; color: var(--color-fg); }
.toolbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }

/* 筛选 select */
.filter-select {
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--color-input);
  border-radius: var(--radius);
  font-size: 13px;
  font-family: var(--font-sans);
  background: var(--color-bg);
  color: var(--color-fg);
  cursor: pointer;
  outline: none;
  transition: border-color var(--transition), box-shadow var(--transition);
}
.filter-select:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(56, 123, 255, 0.15);
}

/* 用户ID筛选输入框 */
.filter-input {
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--color-input);
  border-radius: var(--radius);
  font-size: 13px;
  font-family: var(--font-sans);
  background: var(--color-bg);
  color: var(--color-fg);
  outline: none;
  transition: border-color var(--transition), box-shadow var(--transition);
}
.filter-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(56, 123, 255, 0.15);
}

/* 图标按钮 */
.icon-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  cursor: pointer;
  color: var(--color-fg-muted);
  transition: var(--transition);
}
.icon-btn:hover:not(:disabled) { background: var(--color-muted); color: var(--color-fg); }
.icon-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.icon-btn svg { width: 14px; height: 14px; }

/* 表格 */
.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th {
  text-align: left;
  padding: 10px 16px;
  background: var(--color-muted);
  color: var(--color-fg-muted);
  font-family: var(--font-mono);
  font-weight: 500;
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  white-space: nowrap;
}
.data-table td {
  padding: 12px 16px;
  border-top: 1px solid var(--color-border);
  color: var(--color-fg);
  vertical-align: top;
}
.data-table tbody tr:hover { background: var(--color-muted); }
.sub-text { font-size: 11px; color: var(--color-fg-muted); }
.mono { font-family: var(--font-mono); }

/* 操作类型徽章（胶囊形） */
.action-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  font-family: var(--font-sans);
  white-space: nowrap;
  background: var(--color-accent);
  color: var(--color-accent-fg);
}

/* 结果徽章 */
.status-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  font-family: var(--font-sans);
}
.status-success { background: var(--color-success-subtle); color: var(--color-success-strong); }
.status-fail { background: var(--color-error-subtle); color: var(--color-error-strong); }

/* 空状态 */
.empty-row { text-align: center; color: var(--color-fg-muted); padding: 40px 16px; }

/* 分页 */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  border-top: 1px solid var(--color-border);
}
.page-btn {
  padding: 6px 14px;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 13px;
  font-family: var(--font-sans);
  cursor: pointer;
  color: var(--color-fg);
  transition: var(--transition);
}
.page-btn:hover:not(:disabled) { background: var(--color-muted); }
.page-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.page-info { font-size: 13px; color: var(--color-fg-muted); }
</style>
