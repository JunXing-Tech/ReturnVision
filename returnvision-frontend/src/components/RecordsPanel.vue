<template>
  <div class="records-panel">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2 class="page-title">退货记录</h2>
      <p class="page-subtitle">管理所有退货面单识别记录</p>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-left">
        <el-select
          v-model="filterStatus"
          placeholder="全部状态"
          clearable
          @change="handleFilterChange"
          style="width: 150px"
        >
          <el-option label="全部" value="" />
          <el-option label="待确认" value="pending" />
          <el-option label="已确认" value="confirmed" />
          <el-option label="已同步" value="synced" />
        </el-select>
        <span class="total-text">共 {{ total }} 条</span>
      </div>
      <el-button type="primary" plain round @click="loadRecords">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
    </div>

    <!-- PC端：表格 -->
    <div v-if="!isMobile" class="table-card">
      <el-table
        :data="records"
        v-loading="loading"
        style="width: 100%"
        :header-cell-style="{ background: 'var(--color-bg-secondary)', color: 'var(--color-text-secondary)', fontWeight: 600 }"
        :row-style="{ height: '52px' }"
        empty-text="暂无记录"
      >
        <el-table-column prop="id" label="ID" width="60" align="center" />
        <el-table-column prop="waybillNo" label="运单号" width="150" show-overflow-tooltip />
        <el-table-column prop="expressCompany" label="快递公司" width="100" align="center" />
        <el-table-column prop="recName" label="收件人" width="100" show-overflow-tooltip />
        <el-table-column prop="recPhone" label="收件电话" width="130" show-overflow-tooltip />
        <el-table-column prop="recAddress" label="收件地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="senderName" label="寄件人" width="100" show-overflow-tooltip />
        <el-table-column prop="goods" label="托寄物" width="100" show-overflow-tooltip />
        <el-table-column prop="returnReason" label="退货原因" width="130" show-overflow-tooltip />
        <el-table-column label="分类" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.returnCategory" type="warning" effect="light" size="small" round>
              {{ row.returnCategory }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="light" size="small" round>
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'pending'"
              type="primary"
              size="small"
              round
              @click="confirmRecord(row)"
            >
              <el-icon><Check /></el-icon>
              确认
            </el-button>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next, jumper"
          background
          @current-change="loadRecords"
        />
      </div>
    </div>

    <!-- 移动端：卡片列表 -->
    <div v-else class="card-list">
      <div v-loading="loading">
        <div v-if="records.length === 0 && !loading" class="empty-state">
          <el-icon :size="48" color="var(--color-text-muted)"><Document /></el-icon>
          <p class="empty-text">暂无记录</p>
        </div>
        <div
          v-for="record in records"
          :key="record.id"
          class="record-card"
        >
          <!-- 卡片头部 -->
          <div class="card-top">
            <span class="card-id">#{{ record.id }}</span>
            <el-tag :type="statusTagType(record.status)" effect="light" size="small" round>
              {{ statusLabel(record.status) }}
            </el-tag>
          </div>

          <!-- 卡片内容 -->
          <div class="card-fields">
            <div class="card-field">
              <span class="field-label">运单号</span>
              <span class="field-value">{{ record.waybillNo || '-' }}</span>
            </div>
            <div class="card-field">
              <span class="field-label">快递公司</span>
              <span class="field-value">{{ record.expressCompany || '-' }}</span>
            </div>
            <div class="card-field-row">
              <div class="card-field flex-1">
                <span class="field-label">收件人</span>
                <span class="field-value">{{ record.recName || '-' }}</span>
              </div>
              <div class="card-field flex-1">
                <span class="field-label">收件电话</span>
                <span class="field-value">{{ record.recPhone || '-' }}</span>
              </div>
            </div>
            <div class="card-field">
              <span class="field-label">收件地址</span>
              <span class="field-value">{{ record.recAddress || '-' }}</span>
            </div>
            <div class="card-field-row">
              <div class="card-field flex-1">
                <span class="field-label">寄件人</span>
                <span class="field-value">{{ record.senderName || '-' }}</span>
              </div>
              <div class="card-field flex-1">
                <span class="field-label">托寄物</span>
                <span class="field-value">{{ record.goods || '-' }}</span>
              </div>
            </div>
            <div v-if="record.returnReason" class="card-field">
              <span class="field-label">退货原因</span>
              <span class="field-value">{{ record.returnReason }}</span>
            </div>
            <div v-if="record.returnCategory" class="card-field">
              <span class="field-label">分类</span>
              <el-tag type="warning" effect="light" size="small" round>
                {{ record.returnCategory }}
              </el-tag>
            </div>
          </div>

          <!-- 卡片底部 -->
          <div class="card-bottom">
            <span class="card-time">{{ formatTime(record.createdAt) }}</span>
            <el-button
              v-if="record.status === 'pending'"
              type="primary"
              size="small"
              round
              @click="confirmRecord(record)"
            >
              <el-icon><Check /></el-icon>
              确认
            </el-button>
          </div>
        </div>
      </div>

      <!-- 移动端分页 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="page"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          background
          small
          @current-change="loadRecords"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Document, Refresh, Check } from '@element-plus/icons-vue';
import { useMobile } from '../composables/useMobile';
import api from '../api';

const { isMobile } = useMobile();

const loading = ref(false);
const records = ref([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const filterStatus = ref('');

// 加载记录列表
const loadRecords = async () => {
  loading.value = true;
  try {
    const res = await api.getRecords(filterStatus.value, page.value, pageSize);
    if (res.data.code === 0) {
      const data = res.data.data;
      records.value = data.records || data.list || [];
      total.value = data.total || 0;
    }
  } catch (err) {
    ElMessage.error('加载失败：' + (err.response?.data?.msg || err.message));
  } finally {
    loading.value = false;
  }
};

// 筛选变化时重置到第一页
const handleFilterChange = () => {
  page.value = 1;
  loadRecords();
};

// 确认记录写入飞书
const confirmRecord = async (row) => {
  try {
    const res = await api.confirm(row.id);
    if (res.data.code === 0) {
      ElMessage.success('已写入飞书');
      loadRecords();
    } else {
      ElMessage.error(res.data.msg || '确认失败');
    }
  } catch (err) {
    ElMessage.error('确认失败：' + (err.response?.data?.msg || err.message));
  }
};

// 状态标签映射
const statusTagType = (s) => {
  const map = { pending: 'warning', confirmed: 'primary', synced: 'success', failed: 'danger' };
  return map[s] || 'info';
};

const statusLabel = (s) => {
  const map = { pending: '待确认', confirmed: '已确认', synced: '已同步', failed: '失败' };
  return map[s] || s;
};

// 格式化创建时间，兼容 ISO 字符串和数组格式
const formatTime = (time) => {
  if (!time) return '-';
  // 兼容 Jackson 数组格式 [2026, 7, 15, 12, 30, 0]
  if (Array.isArray(time)) {
    const [y, m, d, h = 0, min = 0] = time;
    const pad = (n) => String(n).padStart(2, '0');
    return `${y}-${pad(m)}-${pad(d)} ${pad(h)}:${pad(min)}`;
  }
  const date = new Date(time);
  if (isNaN(date.getTime())) return String(time);
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

onMounted(() => loadRecords());
</script>

<style scoped>
.records-panel {
  width: 100%;
}

/* 页面标题 */
.page-header {
  margin-bottom: 20px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 4px;
}

.page-subtitle {
  font-size: 14px;
  color: var(--color-text-secondary);
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: 14px 20px;
  margin-bottom: 16px;
  border: 1px solid var(--color-border);
}

.filter-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.total-text {
  font-size: 13px;
  color: var(--color-text-muted);
}

/* 表格卡片 */
.table-card {
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border);
  overflow: hidden;
}

.table-card :deep(.el-table) {
  border-radius: var(--radius-lg);
  --el-table-border-color: var(--color-border);
  --el-table-header-bg-color: var(--color-bg-secondary);
}

.table-card :deep(.el-table th.el-table__cell) {
  font-size: 13px;
}

.table-card :deep(.el-table td.el-table__cell) {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.table-card :deep(.el-table__row:hover > td.el-table__cell) {
  background: var(--el-color-primary-light-9) !important;
}

.table-card :deep(.el-table .cell) {
  line-height: 1.5;
}

.text-muted {
  color: var(--color-text-muted);
  font-size: 13px;
}

.time-text {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}

/* 分页 */
.pagination-bar {
  display: flex;
  justify-content: center;
  padding: 16px 0 12px;
}

.pagination-bar :deep(.el-pagination.is-background .el-pager li.is-active) {
  background-color: var(--color-primary);
}

/* ===== 移动端卡片列表 ===== */
.card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.record-card {
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--color-border);
  padding: 16px;
  transition: box-shadow var(--transition);
}

.record-card:hover {
  box-shadow: var(--shadow-md);
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--color-border);
}

.card-id {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-primary);
}

.card-fields {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.card-field {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.card-field-row {
  display: flex;
  gap: 16px;
}

.flex-1 {
  flex: 1;
}

.field-label {
  font-size: 12px;
  color: var(--color-text-muted);
  font-weight: 500;
}

.field-value {
  font-size: 14px;
  color: var(--color-text);
  word-break: break-all;
}

.card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 1px solid var(--color-border);
}

.card-time {
  font-size: 13px;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  background: #fff;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}

.empty-text {
  font-size: 15px;
  color: var(--color-text-muted);
  margin-top: 12px;
}

/* ===== 移动端响应式 ===== */
@media (max-width: 768px) {
  .filter-bar {
    flex-direction: column;
    gap: 12px;
    align-items: stretch;
    padding: 12px 16px;
  }

  .filter-left {
    justify-content: space-between;
  }

  .filter-left .el-select {
    flex: 1;
  }
}
</style>
