<template>
  <div class="records-panel">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select v-model="filterStatus" placeholder="状态筛选" clearable @change="loadRecords" style="width: 150px">
        <el-option label="待确认" value="pending" />
        <el-option label="已确认" value="confirmed" />
        <el-option label="已同步" value="synced" />
      </el-select>
      <el-button type="primary" @click="loadRecords">刷新</el-button>
    </div>

    <!-- 记录表格 -->
    <el-table :data="records" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="waybillNo" label="运单号" width="130" />
      <el-table-column prop="expressCompany" label="快递公司" width="90" />
      <el-table-column prop="recName" label="收件人" width="120" show-overflow-tooltip />
      <el-table-column prop="recPhone" label="收件电话" width="130" />
      <el-table-column prop="recAddress" label="收件地址" min-width="200" show-overflow-tooltip />
      <el-table-column prop="senderName" label="寄件人" width="90" />
      <el-table-column prop="goods" label="托寄物" width="90" show-overflow-tooltip />
      <el-table-column prop="returnReason" label="退货原因" width="120" show-overflow-tooltip />
      <el-table-column prop="returnCategory" label="分类" width="80" />
      <el-table-column label="状态" width="90" fixed="right">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'pending'" type="primary" size="small" @click="confirmRecord(row)">确认</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="page"
        :page-size="20"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadRecords"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import api from '../api';

const loading = ref(false);
const records = ref([]);
const total = ref(0);
const page = ref(1);
const filterStatus = ref('');

const loadRecords = async () => {
  loading.value = true;
  try {
    const res = await api.getRecords(filterStatus.value, page.value, 20);
    if (res.data.code === 0) {
      records.value = res.data.data.records;
      total.value = res.data.data.total;
    }
  } catch (err) {
    ElMessage.error('加载失败：' + (err.response?.data?.msg || err.message));
  } finally {
    loading.value = false;
  }
};

const confirmRecord = async (row) => {
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
  }
};

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
.records-panel { max-width: 1400px; margin: 0 auto; }
.filter-bar { margin-bottom: 15px; display: flex; gap: 10px; }
.pagination-bar { margin-top: 15px; display: flex; justify-content: center; }
</style>
