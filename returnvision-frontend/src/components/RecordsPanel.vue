<template>
  <!-- 步骤1：记录管理页面外壳 -->
  <div class="screen-shell">
    <!-- 步骤2：紧凑型 Hero 区 -->
    <section class="hero">
      <div class="eyebrow">记录</div>
      <h1 class="hero-title">退货记录</h1>
      <p class="hero-subtitle">全部退货识别记录 · 支持批量确认和编辑</p>
    </section>

    <!-- 步骤3：批量操作栏（选中后出现） -->
    <div v-if="selectedCount > 0" class="batch-bar">
      <div class="batch-left">
        <span class="batch-text">已选 {{ selectedCount }} 条待确认记录</span>
        <button class="btn-ghost" @click="clearSelection">
          <X /> 取消选择
        </button>
      </div>
      <div class="batch-right">
        <button class="btn-danger" :disabled="batchDeleting" @click="batchDelete">
          <span v-if="batchDeleting" class="spin"></span>
          <X v-else />
          {{ batchDeleting ? '删除中...' : '批量删除' }}
        </button>
        <button class="btn-primary" :disabled="batchConfirming" @click="batchConfirm">
          <span v-if="batchConfirming" class="spin"></span>
          <Send v-else />
          {{ batchConfirming ? '确认中...' : '批量确认写入飞书' }}
        </button>
      </div>
    </div>

    <!-- 步骤4：记录卡片 -->
    <section class="card">
      <!-- 卡片头部 + 工具栏 -->
      <div class="card-header">
        <div class="card-heading">
          <div class="eyebrow">数据</div>
          <h3 class="card-title">退货记录列表</h3>
        </div>
        <div class="toolbar">
          <el-select v-model="filterStatus" size="small" style="width: 120px" @change="onFilterChange">
            <el-option label="全部" value="" />
            <el-option label="待确认" value="pending" />
            <el-option label="已同步" value="synced" />
          </el-select>
          <div class="search-shell">
            <Search />
            <input v-model="searchQuery" class="search-input" placeholder="搜索运单号、收件人..." />
          </div>
          <button class="icon-btn" :disabled="loading" @click="loadRecords">
            <Refresh />
          </button>
          <button class="btn-export" :disabled="exporting" @click="exportRecords">
            <Download />
            {{ exporting ? '导出中...' : '导出 Excel' }}
          </button>
        </div>
      </div>

      <!-- 步骤5：数据表格（密集型） -->
      <div v-loading="loading" class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th class="col-check">
                <div
                  :class="['cb', { checked: allPendingSelected, disabled: pendingCount === 0 }]"
                  @click="toggleSelectAll"
                ></div>
              </th>
              <th>ID</th>
              <th>运单号</th>
              <th>快递公司</th>
              <th>收件人</th>
              <th>退货原因</th>
              <th>分类</th>
              <th>状态</th>
              <th>时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in records"
              :key="row.id"
              :class="isOverdue(row) ? 'row-overdue' : (row.status === 'pending' ? 'row-pending' : 'row-synced')"
            >
              <!-- 选择列 -->
              <td>
                <div
                  :class="['cb', { checked: selectedIds.has(row.id), disabled: row.status !== 'pending' }]"
                  @click="row.status === 'pending' && toggleSelect(row.id)"
                ></div>
              </td>
              <td>{{ row.id }}</td>
              <td class="mono">{{ row.waybillNo || '-' }}</td>
              <td>{{ row.expressCompany || '-' }}</td>
              <td>{{ row.recName || '-' }}</td>
              <td class="ellipsis">{{ row.returnReason || '-' }}</td>
              <td>{{ row.returnCategory || '-' }}</td>
              <!-- 状态胶囊 -->
              <td>
                <span :class="['table-pill', row.status === 'pending' ? 'pill-pending' : 'pill-synced']">
                  {{ statusLabel(row.status) }}
                </span>
              </td>
              <td class="mono">{{ formatTime(row.createdAt) }}</td>
              <!-- 操作列 -->
              <td>
                <div v-if="row.status === 'pending'" class="row-actions">
                  <button class="table-action" @click="confirmRecord(row)" :disabled="confirmingId === row.id">
                    <span v-if="confirmingId === row.id" class="spin"></span>
                    <Send v-else /> 确认
                  </button>
                  <button class="table-action" @click="editRecord(row)">
                    <Pen /> 编辑
                  </button>
                  <button class="table-action table-action-danger" @click="deleteRecord(row)" :disabled="deletingId === row.id">
                    <span v-if="deletingId === row.id" class="spin"></span>
                    <X v-else /> 删除
                  </button>
                </div>
                <span v-else class="action-done">
                  <CircleCheck /> 已确认
                </span>
              </td>
            </tr>
            <!-- 步骤6：空状态 -->
            <tr v-if="records.length === 0 && !loading">
              <td colspan="10" class="empty-cell">
                <div class="empty-state">
                  <div class="empty-icon"><Document /></div>
                  <div class="empty-text">暂无退货记录</div>
                  <div class="empty-hint">上传面单图片识别后将在此显示</div>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 步骤7：表格底部分页 -->
      <div class="table-footer">
        <span class="footer-info">显示 {{ rangeStart }}-{{ rangeEnd }} / 共 {{ total }} 条</span>
        <div class="pagination">
          <button class="page-btn icon" :disabled="page === 1" @click="prevPage">
            <ChevronLeft />
          </button>
          <button
            v-for="(p, idx) in pageRange"
            :key="idx"
            :class="['page-btn', { active: p === page, ellipsis: p === '...' }]"
            :disabled="p === '...'"
            @click="goToPage(p)"
          >
            {{ p }}
          </button>
          <button class="page-btn icon" :disabled="page === totalPages" @click="nextPage">
            <ChevronRight />
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
// 步骤8：组件状态与逻辑
import { ref, computed, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import api from '../api';
import {
  Search, Refresh, Pen, Send, X, CircleCheck,
  ChevronLeft, ChevronRight, Document, Download,
} from '../icons';

const emit = defineEmits(['navigate', 'editRecord']);

const loading = ref(false);
const records = ref([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const filterStatus = ref('');
const searchQuery = ref('');
const selectedIds = ref(new Set());
const confirmingId = ref(null);
const batchConfirming = ref(false);
const deletingId = ref(null);
const batchDeleting = ref(false);

// F02 导出状态
const exporting = ref(false);

// 步骤8.1：加载记录列表
const loadRecords = async () => {
  loading.value = true;
  try {
    const res = await api.getRecords(filterStatus.value, page.value, pageSize);
    if (res.data.code === 0) {
      records.value = res.data.data.records || [];
      total.value = res.data.data.total || 0;
      selectedIds.value = new Set();
    }
  } catch (err) {
    ElMessage.error('加载失败');
    records.value = [];
  } finally {
    loading.value = false;
  }
};

// 步骤8.2：单条复选框切换（用新 Set 触发响应式）
const toggleSelect = (id) => {
  if (selectedIds.value.has(id)) selectedIds.value.delete(id);
  else selectedIds.value.add(id);
  selectedIds.value = new Set(selectedIds.value);
};

// 步骤8.3：全选/取消全选（仅待确认记录）
const toggleSelectAll = () => {
  const ids = pendingIds.value;
  if (ids.every((id) => selectedIds.value.has(id))) {
    ids.forEach((id) => selectedIds.value.delete(id));
  } else {
    ids.forEach((id) => selectedIds.value.add(id));
  }
  selectedIds.value = new Set(selectedIds.value);
};

const clearSelection = () => {
  selectedIds.value = new Set();
};

// 步骤8.4：批量确认写入飞书
const batchConfirm = async () => {
  batchConfirming.value = true;
  try {
    const ids = Array.from(selectedIds.value);
    const res = await api.batchConfirm(ids);
    if (res.data.code === 0) {
      ElMessage.success(`已确认 ${ids.length} 条记录`);
      loadRecords();
    } else {
      ElMessage.error(res.data.msg);
    }
  } catch (err) {
    ElMessage.error('批量确认失败：' + (err.response?.data?.msg || err.message));
  } finally {
    batchConfirming.value = false;
  }
};

// 步骤8.5：单条确认写入飞书
const confirmRecord = async (row) => {
  confirmingId.value = row.id;
  try {
    const res = await api.confirm(row.id);
    if (res.data.code === 0) {
      ElMessage.success('已写入飞书');
      loadRecords();
    } else {
      ElMessage.error(res.data.msg);
    }
  } catch (err) {
    ElMessage.error('确认失败：' + (err.response?.data?.msg || err.message));
  } finally {
    confirmingId.value = null;
  }
};

// 步骤8.6：编辑记录 - 携带完整行数据跳转识别页
const editRecord = (row) => {
  emit('editRecord', row);
};

// 步骤8.6.1：单条删除记录（仅允许删除待确认记录）
const deleteRecord = async (row) => {
  if (!confirm(`确认删除记录 #${row.id}？此操作不可撤销。`)) return;
  deletingId.value = row.id;
  try {
    const res = await api.deleteRecord(row.id);
    if (res.data.code === 0) {
      ElMessage.success('记录已删除');
      loadRecords();
    } else {
      ElMessage.error(res.data.msg);
    }
  } catch (err) {
    ElMessage.error('删除失败：' + (err.response?.data?.msg || err.message));
  } finally {
    deletingId.value = null;
  }
};

// 步骤8.6.2：批量删除记录（仅允许删除待确认记录）
const batchDelete = async () => {
  const ids = Array.from(selectedIds.value);
  if (!ids.length) return;
  if (!confirm(`确认删除选中的 ${ids.length} 条记录？此操作不可撤销。`)) return;
  batchDeleting.value = true;
  try {
    const res = await api.batchDeleteRecords(ids);
    if (res.data.code === 0) {
      const s = res.data.data;
      ElMessage.success(`已删除 ${s.success} 条` + (s.failed > 0 ? `，${s.failed} 条失败` : ''));
      clearSelection();
      loadRecords();
    } else {
      ElMessage.error(res.data.msg);
    }
  } catch (err) {
    ElMessage.error('批量删除失败：' + (err.response?.data?.msg || err.message));
  } finally {
    batchDeleting.value = false;
  }
};

// F02 导出退货记录（带水印的 Excel）
const exportRecords = async () => {
  exporting.value = true;
  try {
    const resp = await api.exportRecords({
      status: filterStatus.value,
    });
    // 创建 blob 并触发下载
    const blob = new Blob([resp.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    const now = new Date();
    const ts = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}_${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}${String(now.getSeconds()).padStart(2, '0')}`;
    link.download = `return_records_${ts}.xlsx`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    ElMessage.success('导出成功，文件已开始下载');
  } catch (err) {
    // blob 类型的错误响应需要特殊解析
    if (err.response?.data instanceof Blob) {
      const text = await err.response.data.text();
      try {
        const json = JSON.parse(text);
        ElMessage.error('导出失败：' + (json.msg || '未知错误'));
      } catch {
        ElMessage.error('导出失败');
      }
    } else {
      ElMessage.error('导出失败：' + (err.response?.data?.msg || err.message));
    }
  } finally {
    exporting.value = false;
  }
};

const statusLabel = (s) =>
  ({ pending: '待确认', confirmed: '已确认', synced: '已同步', failed: '失败' }[s] || s);

// F02 超期预警：待确认且 created_at 超过 24 小时
const isOverdue = (row) => {
  if (row.status !== 'pending' || !row.created_at) return false;
  const created = new Date(row.created_at);
  const hours = (Date.now() - created.getTime()) / (1000 * 60 * 60);
  return hours > 24;
};

const formatTime = (t) => {
  if (!t) return '-';
  if (Array.isArray(t)) {
    const [y, m, d, h = 0, min = 0] = t;
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(min).padStart(2, '0')}`;
  }
  const d = new Date(t);
  if (isNaN(d)) return '-';
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

// 步骤8.7：分页计算
const totalPages = computed(() => Math.ceil(total.value / pageSize) || 1);
const pageRange = computed(() => {
  const pages = [];
  const tp = totalPages.value;
  if (tp <= 7) {
    for (let i = 1; i <= tp; i++) pages.push(i);
  } else {
    pages.push(1, 2, 3);
    if (page.value > 4) pages.push('...');
    if (page.value > 3 && page.value < tp - 2) pages.push(page.value);
    pages.push('...', tp - 1, tp);
  }
  return pages;
});

const goToPage = (p) => {
  if (p === '...' || p === page.value) return;
  page.value = p;
  loadRecords();
};
const prevPage = () => {
  if (page.value > 1) {
    page.value--;
    loadRecords();
  }
};
const nextPage = () => {
  if (page.value < totalPages.value) {
    page.value++;
    loadRecords();
  }
};

// 步骤8.8：选择相关计算属性
const selectedCount = computed(() => selectedIds.value.size);
const pendingIds = computed(() => records.value.filter((r) => r.status === 'pending').map((r) => r.id));
const pendingCount = computed(() => pendingIds.value.length);
const allPendingSelected = computed(
  () => pendingCount.value > 0 && pendingIds.value.every((id) => selectedIds.value.has(id))
);

// 步骤8.9：显示区间
const rangeStart = computed(() => (total.value === 0 ? 0 : (page.value - 1) * pageSize + 1));
const rangeEnd = computed(() => Math.min(page.value * pageSize, total.value));

// 步骤8.10：筛选切换时重置页码
const onFilterChange = () => {
  page.value = 1;
  loadRecords();
};

onMounted(() => loadRecords());
</script>

<style scoped>
/* 步骤9：页面外壳与布局 */
.screen-shell {
  padding: calc(var(--spacing) * 5);
  display: flex;
  flex-direction: column;
  gap: calc(var(--spacing) * 4);
}

/* Hero 区 */
.hero { display: flex; flex-direction: column; gap: 4px; }
.eyebrow {
  font-family: var(--font-mono);
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--color-fg-muted);
}
.hero-title { font-size: 22px; font-weight: 600; color: var(--color-fg); }
.hero-subtitle { font-size: 13px; color: var(--color-fg-muted); }

/* 步骤10：批量操作栏 */
.batch-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--color-warning-subtle);
  border: 1px solid var(--color-warning);
  border-radius: 12px;
}
.batch-left { display: flex; align-items: center; gap: 12px; }
.batch-text { font-size: 13px; font-weight: 600; color: var(--color-fg); }

/* 步骤11：通用按钮 */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  border: none;
  border-radius: var(--radius);
  background: var(--color-primary);
  color: var(--color-primary-fg);
  font-size: 13px;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: var(--transition);
}
.btn-primary:hover { opacity: 0.9; }
.btn-primary:disabled { opacity: 0.55; cursor: not-allowed; }
.btn-primary svg { width: 14px; height: 14px; }

/* F02 导出按钮 */
.btn-export {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: transparent;
  color: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}
.btn-export:hover:not(:disabled) {
  background: var(--color-primary);
  color: var(--color-primary-fg);
}
.btn-export:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.btn-export svg { width: 14px; height: 14px; }

.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: transparent;
  color: var(--color-fg);
  font-size: 12px;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: var(--transition);
}
.btn-ghost:hover { background: var(--color-accent); }
.btn-ghost svg { width: 12px; height: 12px; }

/* 步骤12：卡片 */
.card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow-md);
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-border);
}
.card-heading { display: flex; flex-direction: column; gap: 2px; }
.card-title { font-size: 14px; font-weight: 600; color: var(--color-fg); }
.toolbar { display: flex; align-items: center; gap: 8px; }

/* 步骤13：搜索框 */
.search-shell {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  width: 220px;
  border: 1px solid var(--color-input);
  border-radius: var(--radius);
  padding: 0 12px;
  background: var(--color-bg);
  transition: var(--transition);
}
.search-shell:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(56, 123, 255, 0.15);
}
.search-shell svg { width: 14px; height: 14px; color: var(--color-fg-muted); flex-shrink: 0; }
.search-input {
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  width: 100%;
  font-family: var(--font-sans);
  color: var(--color-fg);
}
.search-input::placeholder { color: var(--color-fg-muted); }

/* 步骤14：图标按钮 */
.icon-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: transparent;
  cursor: pointer;
  transition: var(--transition);
}
.icon-btn:hover { background: var(--color-accent); }
.icon-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.icon-btn svg { width: 14px; height: 14px; color: var(--color-fg-muted); }

/* 步骤15：数据表格 */
.table-wrap { overflow-x: auto; position: relative; min-height: 80px; }
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
}
.data-table th {
  font-family: var(--font-mono);
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-fg-muted);
  border-bottom: 1px solid var(--color-border);
  padding: 8px 12px;
  text-align: left;
  white-space: nowrap;
  background: var(--color-bg);
}
.data-table td {
  font-size: 12.5px;
  border-bottom: 1px solid var(--color-border);
  padding: 10px 12px;
  color: var(--color-fg);
  white-space: nowrap;
}
.col-check { width: 40px; }

/* 步骤16：行状态视觉区分 */
tr.row-pending {
  background: var(--color-warning-subtle);
  box-shadow: inset 2px 0 0 var(--color-warning);
}
tr.row-synced { background: var(--color-bg); }
/* F02 超期预警：待确认超过 24 小时标黄 */
tr.row-overdue {
  background: var(--color-warning-subtle);
  box-shadow: inset 2px 0 0 var(--color-warning-strong);
}
tr.row-overdue:hover { background: var(--color-warning-subtle) !important; opacity: 0.8; }
.data-table tbody tr:hover { background: var(--color-muted); }

/* 步骤17：自定义复选框 */
.cb {
  width: 16px;
  height: 16px;
  border: 1.5px solid var(--color-input);
  border-radius: 3px;
  cursor: pointer;
  position: relative;
  flex-shrink: 0;
  transition: var(--transition);
  background: var(--color-bg);
}
.cb:hover { border-color: var(--color-primary); }
.cb.checked {
  background: var(--color-primary);
  border-color: var(--color-primary);
}
.cb.checked::after {
  content: '';
  position: absolute;
  left: 4px;
  top: 1px;
  width: 5px;
  height: 9px;
  border: solid white;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}
.cb.disabled {
  opacity: 0.35;
  cursor: not-allowed;
  background: var(--color-bg);
}
.cb.disabled:hover { border-color: var(--color-input); }

/* 步骤18：状态胶囊 */
.table-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}
.pill-pending {
  background: var(--color-warning-subtle);
  color: var(--color-warning-strong);
}
.pill-synced {
  background: var(--color-success-subtle);
  color: var(--color-success-strong);
}

/* 步骤19：操作按钮 */
.table-action {
  height: 26px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: transparent;
  font-size: 12px;
  font-family: var(--font-sans);
  color: var(--color-primary);
  cursor: pointer;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: var(--transition);
}
.table-action:hover { background: var(--color-accent); border-color: var(--color-fg-muted); }
.table-action svg { width: 12px; height: 12px; }

.action-done {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-fg-muted);
}
.action-done svg { width: 12px; height: 12px; color: var(--color-success); }

/* 步骤20：等宽与省略 */
.mono { font-family: var(--font-mono); font-size: 12px; }
.ellipsis { max-width: 180px; overflow: hidden; text-overflow: ellipsis; }

/* 步骤21：空状态 */
.empty-cell { padding: 48px 16px !important; text-align: center; }
.empty-state { display: flex; flex-direction: column; align-items: center; gap: 8px; }
.empty-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--color-muted);
  color: var(--color-fg-muted);
}
.empty-icon svg { width: 20px; height: 20px; }
.empty-text { font-size: 14px; font-weight: 600; color: var(--color-fg); }
.empty-hint { font-size: 12px; color: var(--color-fg-muted); }

/* 步骤22：表格底部 */
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid var(--color-border);
  gap: 12px;
  flex-wrap: wrap;
}
.footer-info { font-size: 12px; color: var(--color-fg-muted); white-space: nowrap; }
.pagination { display: flex; align-items: center; gap: 4px; }

.page-btn {
  height: 26px;
  min-width: 26px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: transparent;
  font-size: 12px;
  font-family: var(--font-sans);
  color: var(--color-fg);
  cursor: pointer;
  padding: 0 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition);
}
.page-btn:hover:not(:disabled):not(.active) { background: var(--color-accent); }
.page-btn.active {
  background: var(--color-primary);
  color: var(--color-primary-fg);
  border-color: var(--color-primary);
}
.page-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.page-btn.ellipsis { border: none; background: transparent; cursor: default; opacity: 0.4; }
.page-btn.icon { padding: 0; }
.page-btn.icon svg { width: 14px; height: 14px; }

/* 步骤23：加载旋转 */
.spin {
  width: 12px;
  height: 12px;
  border: 1.5px solid var(--color-primary-fg);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
  display: inline-block;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* 步骤23.5：批量操作栏右侧按钮组 */
.batch-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 步骤23.6：删除按钮 */
.btn-danger {
  height: 34px;
  background: transparent;
  color: var(--color-error);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 16px;
  transition: var(--transition);
  font-family: var(--font-sans);
}
.btn-danger:hover:not(:disabled) { background: var(--color-error-subtle); border-color: var(--color-error); }
.btn-danger:disabled { opacity: 0.5; cursor: not-allowed; }

/* 步骤23.7：行内操作按钮组 */
.row-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}
.table-action:disabled { opacity: 0.5; cursor: not-allowed; }
.table-action-danger {
  color: var(--color-error);
}
.table-action-danger:hover {
  background: var(--color-error-subtle);
}

/* 步骤24：Element Plus 下拉框样式对齐 */
.toolbar :deep(.el-select .el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--color-input) inset;
  border-radius: var(--radius);
  min-height: 34px;
  background: var(--color-bg);
}
.toolbar :deep(.el-select .el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--color-primary) inset;
}

/* 步骤25：响应式 - 小屏表格横向滚动 */
@media (max-width: 992px) {
  .screen-shell { padding: calc(var(--spacing) * 3); }
  .card-header { flex-wrap: wrap; }
  .search-shell { width: 100%; }
  .table-wrap { overflow-x: auto; }
}
</style>
