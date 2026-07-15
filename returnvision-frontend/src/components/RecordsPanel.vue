<template>
  <div class="records-panel">
    <!-- 筛选栏 -->
    <div class="toolbar-card">
      <div class="toolbar-left">
        <span class="toolbar-title">
          <el-icon><Document /></el-icon>
          退货记录
        </span>
        <el-divider direction="vertical" />
        <el-select
          v-model="filterStatus"
          placeholder="全部状态"
          clearable
          @change="handleFilterChange"
          style="width: 150px"
        >
          <el-option label="待确认" value="pending" />
          <el-option label="已确认" value="confirmed" />
          <el-option label="已同步" value="synced" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <span class="total-text">共 {{ total }} 条</span>
        <el-button type="primary" plain round @click="loadRecords">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 记录表格 -->
    <div class="table-card">
      <el-table
        :data="records"
        v-loading="loading"
        style="width: 100%"
        :header-cell-style="{ background: '#f8fafc', color: '#475569', fontWeight: 600 }"
        :row-style="{ height: '52px' }"
        empty-text="暂无记录"
      >
        <el-table-column prop="id" label="ID" width="60" align="center" />
        <el-table-column prop="waybillNo" label="运单号" width="140" show-overflow-tooltip />
        <el-table-column prop="expressCompany" label="快递公司" width="90" align="center" />
        <el-table-column prop="recName" label="收件人" width="100" show-overflow-tooltip />
        <el-table-column prop="recPhone" label="收件电话" width="130" show-overflow-tooltip />
        <el-table-column prop="recAddress" label="收件地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="senderName" label="寄件人" width="90" show-overflow-tooltip />
        <el-table-column prop="goods" label="托寄物" width="90" show-overflow-tooltip />
        <el-table-column prop="returnReason" label="退货原因" width="130" show-overflow-tooltip />
        <el-table-column prop="returnCategory" label="分类" width="90" align="center">
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
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Document, Refresh, Check } from '@element-plus/icons-vue';
import api from '../api';

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

onMounted(() => loadRecords());
</script>

<style scoped>
.records-panel { width: 100%; }

/* 筛选栏 */
.toolbar-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: var(--rv-card-radius);
  box-shadow: var(--rv-shadow);
  padding: 14px 20px;
  margin-bottom: 16px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
.toolbar-title .el-icon { color: #0d9488; font-size: 18px; }

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.total-text {
  font-size: 13px;
  color: #94a3b8;
}

/* 表格卡片 */
.table-card {
  background: #fff;
  border-radius: var(--rv-card-radius);
  box-shadow: var(--rv-shadow);
  padding: 4px;
  overflow: hidden;
}

/* 表格样式 */
.table-card :deep(.el-table) {
  border-radius: 12px;
  --el-table-border-color: #f0f5f4;
  --el-table-header-bg-color: #f8fafc;
}
.table-card :deep(.el-table th.el-table__cell) {
  font-size: 13px;
}
.table-card :deep(.el-table td.el-table__cell) {
  font-size: 13px;
  color: #475569;
}
.table-card :deep(.el-table__row:hover > td.el-table__cell) {
  background: #f7fdfb !important;
}
.table-card :deep(.el-table .cell) {
  line-height: 1.5;
}

.text-muted { color: #cbd5e1; font-size: 13px; }

/* 分页 */
.pagination-bar {
  display: flex;
  justify-content: center;
  padding: 16px 0 12px;
}
.pagination-bar :deep(.el-pagination.is-background .el-pager li.is-active) {
  background-color: #0d9488;
}
</style>
