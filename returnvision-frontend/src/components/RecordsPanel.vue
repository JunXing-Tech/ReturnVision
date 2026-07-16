<template>
  <div class="records-panel">
    <div class="filter-bar">
      <el-select v-model="filterStatus" placeholder="状态筛选" clearable @change="() => { page = 1; loadRecords(); }" style="width: 140px">
        <el-option label="全部" value="" />
        <el-option label="待确认" value="pending" />
        <el-option label="已确认" value="confirmed" />
        <el-option label="已同步" value="synced" />
      </el-select>
      <el-button type="primary" @click="loadRecords">刷新</el-button>
      <span class="total-text">共 {{ total }} 条</span>
    </div>

    <!-- PC 表格 -->
    <el-table v-if="!isMobile" :data="records" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="waybillNo" label="运单号" width="130" />
      <el-table-column prop="expressCompany" label="快递公司" width="90" />
      <el-table-column prop="recName" label="收件人" width="110" show-overflow-tooltip />
      <el-table-column prop="recPhone" label="收件电话" width="130" />
      <el-table-column prop="recAddress" label="收件地址" min-width="180" show-overflow-tooltip />
      <el-table-column prop="senderName" label="寄件人" width="90" />
      <el-table-column prop="goods" label="托寄物" width="80" show-overflow-tooltip />
      <el-table-column prop="returnReason" label="退货原因" width="110" show-overflow-tooltip />
      <el-table-column prop="returnCategory" label="分类" width="80" />
      <el-table-column label="状态" width="90" fixed="right">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small" round>{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="140" fixed="right">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'pending'" type="primary" size="small" @click="openEdit(row)">编辑确认</el-button>
          <el-button size="small" @click="openView(row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 移动端卡片 -->
    <div v-if="isMobile" v-loading="loading" class="card-list">
      <div v-for="r in records" :key="r.id" class="record-card">
        <div class="card-top">
          <span class="card-id">#{{ r.id }}</span>
          <el-tag :type="statusType(r.status)" size="small" round>{{ statusLabel(r.status) }}</el-tag>
        </div>
        <div class="card-row"><span class="label">运单号</span><span>{{ r.waybillNo || '-' }}</span></div>
        <div class="card-row"><span class="label">快递公司</span><span>{{ r.expressCompany || '-' }}</span></div>
        <div class="card-row"><span class="label">收件人</span><span>{{ r.recName || '-' }} {{ r.recPhone || '' }}</span></div>
        <div class="card-row"><span class="label">收件地址</span><span class="ellipsis">{{ r.recAddress || '-' }}</span></div>
        <div class="card-row"><span class="label">退货原因</span><span>{{ r.returnReason || '-' }}</span></div>
        <div class="card-row"><span class="label">时间</span><span>{{ formatTime(r.createdAt) }}</span></div>
        <div class="card-actions">
          <el-button v-if="r.status === 'pending'" type="primary" size="small" @click="openEdit(r)">编辑确认</el-button>
          <el-button size="small" @click="openView(r)">查看</el-button>
        </div>
      </div>
      <div v-if="records.length === 0 && !loading" class="empty">暂无记录</div>
    </div>

    <div class="pagination">
      <el-pagination v-model:current-page="page" :page-size="20" :total="total"
        layout="total, prev, pager, next" @current-change="loadRecords" />
    </div>

    <!-- 编辑对话框 -->
    <el-dialog v-model="editVisible" :title="editReadOnly ? '查看记录' : '编辑并确认'" width="600px">
      <el-form :model="editForm" label-width="90px" :disabled="editReadOnly">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="运单号"><el-input v-model="editForm.waybillNo" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="快递公司"><el-input v-model="editForm.expressCompany" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="收件人"><el-input v-model="editForm.recName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="收件电话"><el-input v-model="editForm.recPhone" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="收件地址"><el-input v-model="editForm.recAddress" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="寄件人"><el-input v-model="editForm.senderName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="寄件电话"><el-input v-model="editForm.senderPhone" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="寄件地址"><el-input v-model="editForm.senderAddress" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="托寄物"><el-input v-model="editForm.goods" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="退货分类"><el-input v-model="editForm.returnCategory" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="退货原因"><el-input v-model="editForm.returnReason" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">关闭</el-button>
        <el-button v-if="!editReadOnly" type="primary" @click="handleEditConfirm" :loading="confirming">确认写入飞书</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { useMobile } from '../composables/useMobile';
import api from '../api';

const { isMobile } = useMobile();
const loading = ref(false);
const records = ref([]);
const total = ref(0);
const page = ref(1);
const filterStatus = ref('');
const editVisible = ref(false);
const editReadOnly = ref(false);
const confirming = ref(false);
const editForm = reactive({});
let editRowId = null;

const loadRecords = async () => {
  loading.value = true;
  try {
    const res = await api.getRecords(filterStatus.value, page.value, 20);
    if (res.data.code === 0) { records.value = res.data.data.records; total.value = res.data.data.total; }
  } catch { ElMessage.error('加载失败'); } finally { loading.value = false; }
};

const openEdit = (row) => {
  editReadOnly.value = false;
  editRowId = row.id;
  Object.keys(editForm).forEach(k => delete editForm[k]);
  Object.assign(editForm, {
    waybillNo: row.waybillNo || '', expressCompany: row.expressCompany || '',
    recName: row.recName || '', recPhone: row.recPhone || '', recAddress: row.recAddress || '',
    senderName: row.senderName || '', senderPhone: row.senderPhone || '', senderAddress: row.senderAddress || '',
    goods: row.goods || '', returnReason: row.returnReason || '', returnCategory: row.returnCategory || '',
  });
  editVisible.value = true;
};

const openView = (row) => {
  editReadOnly.value = true;
  editRowId = row.id;
  Object.keys(editForm).forEach(k => delete editForm[k]);
  Object.assign(editForm, {
    waybillNo: row.waybillNo || '', expressCompany: row.expressCompany || '',
    recName: row.recName || '', recPhone: row.recPhone || '', recAddress: row.recAddress || '',
    senderName: row.senderName || '', senderPhone: row.senderPhone || '', senderAddress: row.senderAddress || '',
    goods: row.goods || '', returnReason: row.returnReason || '', returnCategory: row.returnCategory || '',
  });
  editVisible.value = true;
};

const handleEditConfirm = async () => {
  confirming.value = true;
  try {
    const res = await api.confirm(editRowId, editForm);
    if (res.data.code === 0) {
      ElMessage.success('已写入飞书');
      editVisible.value = false;
      loadRecords();
    } else ElMessage.error(res.data.msg);
  } catch { ElMessage.error('确认失败'); } finally { confirming.value = false; }
};

const statusType = (s) => ({ pending: 'warning', confirmed: '', synced: 'success', failed: 'danger' }[s] || 'info');
const statusLabel = (s) => ({ pending: '待确认', confirmed: '已确认', synced: '已同步', failed: '失败' }[s] || s);
const formatTime = (t) => {
  if (!t) return '-';
  if (Array.isArray(t)) { const [y,m,d,h=0,min=0] = t; return `${y}-${String(m).padStart(2,'0')}-${String(d).padStart(2,'0')} ${String(h).padStart(2,'0')}:${String(min).padStart(2,'0')}`; }
  const d = new Date(t); if (isNaN(d)) return '-';
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
};

onMounted(() => loadRecords());
</script>

<style scoped>
.records-panel { display: flex; flex-direction: column; gap: 16px; }
.filter-bar { display: flex; align-items: center; gap: 12px; }
.total-text { font-size: 13px; color: var(--color-text-muted); }
.pagination { display: flex; justify-content: center; margin-top: 8px; }
.card-list { display: flex; flex-direction: column; gap: 12px; }
.record-card { background: #fff; border-radius: var(--radius-lg); padding: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); border: 1px solid var(--color-border); }
.card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.card-id { font-weight: 700; }
.card-row { display: flex; gap: 8px; font-size: 13px; margin-bottom: 6px; }
.card-row .label { color: var(--color-text-muted); width: 70px; flex-shrink: 0; }
.card-actions { margin-top: 12px; display: flex; gap: 8px; }
.ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty { text-align: center; padding: 40px; color: var(--color-text-muted); }
</style>
