<template>
  <!-- 步骤1：模式切换 + 多态视图 -->
  <div class="screen-shell">
    <!-- 模式 Tab -->
    <div class="mode-tabs">
      <button :class="['mode-tab', { active: mode === 'single' }]" @click="switchMode('single')">单张识别</button>
      <button :class="['mode-tab', { active: mode === 'batch' }]" @click="switchMode('batch')">批量识别</button>
    </div>

    <!-- ===== 状态1：空闲（上传区） ===== -->
    <div v-if="state === 'idle'" class="idle-view">
      <!-- 单张上传区 -->
      <div v-if="mode === 'single'" class="upload-zone" @click="$refs.fileInput.click()" @dragover.prevent="dragOver = true" @drop.prevent="handleDrop" @dragleave.prevent="dragOver = false" :class="{ 'drag-active': dragOver }">
        <input ref="fileInput" type="file" accept="image/*" class="hidden-input" @change="handleFileSelect" />
        <CloudUpload class="upload-icon" />
        <p class="upload-title">点击或拖拽上传快递面单图片</p>
        <p class="upload-hint">支持 JPG / PNG，最大 10MB · 双引擎OCR + DeepSeek分析</p>
      </div>

      <!-- 批量上传区 -->
      <div v-else class="upload-zone" @click="$refs.batchInput.click()" @dragover.prevent="dragOver = true" @drop.prevent="handleBatchDrop" @dragleave.prevent="dragOver = false" :class="{ 'drag-active': dragOver }">
        <input ref="batchInput" type="file" accept="image/*" multiple class="hidden-input" @change="handleBatchSelect" />
        <CloudUpload class="upload-icon" />
        <p class="upload-title">拖拽多张快递面单图片到此处</p>
        <p class="upload-hint">或点击选择文件 · 单次最多 20 张 · 每张最大 10MB</p>
      </div>
    </div>

    <!-- ===== 状态2：单张处理中（SSE时间线） ===== -->
    <div v-else-if="state === 'processing'" class="processing-view">
      <div class="processing-card">
        <div class="processing-header">
          <div class="spinner-lg"></div>
          <div>
            <div class="processing-title">正在识别...</div>
            <div class="processing-subtitle">平均耗时 8 秒，请勿关闭页面</div>
          </div>
        </div>

        <div class="step-timeline">
          <div v-for="(s, idx) in steps" :key="s.step" class="step-row">
            <div class="step-icon">
              <CircleCheck v-if="s.status === 'done'" class="icon-done" />
              <div v-else-if="s.status === 'active'" class="spinner-sm"></div>
              <div v-else class="circle-pending"></div>
            </div>
            <div v-if="idx < steps.length - 1" :class="['step-connector', { done: s.status === 'done' }]"></div>
            <div class="step-content">
              <div :class="['step-label', s.status]">{{ s.label }}</div>
              <div class="step-desc">{{ s.desc }}</div>
              <div v-if="s.subSteps && s.subSteps.length" class="sub-steps">
                <div v-for="ss in s.subSteps" :key="ss.name" class="sub-step" :class="ss.status">
                  <div v-if="ss.status === 'active'" class="spinner-sm"></div>
                  <CircleCheck v-else-if="ss.status === 'done'" class="icon-done-sm" />
                  <div v-else class="circle-pending-sm"></div>
                  <span>{{ ss.name }} {{ ss.status === 'active' ? '识别中...' : ss.status === 'done' ? '已完成' : '等待中' }}</span>
                </div>
              </div>
              <div v-if="s.meta" class="step-meta">{{ s.meta }}</div>
            </div>
          </div>
        </div>

        <div class="processing-footer">
          <button class="btn-ghost" @click="cancelUpload"><X /><span>取消识别</span></button>
          <span class="step-progress">步骤 {{ currentStepNum }}/{{ steps.length }} · {{ currentStepLabel }}</span>
        </div>
      </div>
      <div class="tech-note">SSE 实时推送 · 每步完成后自动打勾 · 全部完成后自动跳转结果页</div>
    </div>

    <!-- ===== 状态3：批量处理中（多任务并行） ===== -->
    <div v-else-if="state === 'batchProcessing'" class="batch-processing-view">
      <div class="bp-header">
        <div class="spinner-lg"></div>
        <div>
          <div class="processing-title">正在批量识别 {{ batchTasks.length }} 张面单...</div>
          <div class="processing-subtitle">已完成 {{ batchDoneCount }} / {{ batchTasks.length }} · 请勿关闭页面</div>
        </div>
        <div class="bp-progress-bar">
          <div class="bp-progress-fill" :style="{ width: batchProgressPct + '%' }"></div>
        </div>
      </div>

      <div class="bp-task-grid">
        <div v-for="task in batchTasks" :key="task.id" :class="['bp-task-card', task.overallStatus]">
          <div class="bp-task-header">
            <span class="bp-task-name">{{ task.filename }}</span>
            <CircleCheck v-if="task.overallStatus === 'done'" class="icon-done-sm" />
            <div v-else-if="task.overallStatus === 'active'" class="spinner-sm"></div>
            <CircleAlert v-else-if="task.overallStatus === 'error'" class="icon-sm" />
            <div v-else class="circle-pending-sm"></div>
          </div>
          <!-- 紧凑步骤条 -->
          <div class="bp-mini-steps">
            <div v-for="(s, i) in task.steps" :key="i" :class="['bp-mini-step', s.status]">
              <span class="bp-mini-dot"></span>
              <span class="bp-mini-label">{{ shortStepLabel(i) }}</span>
            </div>
          </div>
          <div v-if="task.overallStatus === 'error'" class="bp-task-error">{{ task.errorMsg || '识别失败' }}</div>
        </div>
      </div>
    </div>

    <!-- ===== 状态4：结果（可编辑表单） ===== -->
    <div v-else-if="state === 'result' && result" class="result-view">
      <!-- 编辑来源提示 -->
      <div v-if="isEditing" class="edit-banner">
        <Pen class="banner-icon" />
        <span>正在编辑记录 #{{ result.record_id }} · 来自退货记录页</span>
      </div>

      <!-- 状态横幅 -->
      <div :class="['status-banner', statusLevel]">
        <CircleCheck v-if="result.cross_validation === 'accept'" class="banner-icon" />
        <CircleAlert v-else class="banner-icon" />
        <div class="banner-text">
          <p class="banner-title">{{ statusTitle }}</p>
          <p class="banner-desc">{{ statusDesc }}</p>
        </div>
        <div class="banner-meta">
          <span class="meta-item">置信度：<b>{{ confidenceLabel }}</b></span>
          <span class="meta-item">来源：<b>{{ sourceLabel }}</b></span>
        </div>
      </div>

      <!-- 双栏面板 -->
      <div class="panel-grid">
        <!-- 左栏：证据 -->
        <div class="left-col">
          <div class="card">
            <div class="card-header"><span class="eyebrow">原图</span><h3 class="card-title">面单照片</h3></div>
            <div v-if="result.image_url" class="image-preview">
              <img :src="result.image_url" alt="面单照片" class="waybill-img" />
            </div>
            <div v-else class="image-placeholder">
              <Image class="placeholder-icon" />
              <span>面单照片预览</span>
            </div>
            <div v-if="form.waybill_no" class="image-meta">{{ form.waybill_no }} · {{ form.express_company || '-' }}</div>
          </div>

          <div v-if="validationErrors.length || validationWarnings.length" class="card">
            <div class="card-header"><span class="eyebrow">校验</span><h3 class="card-title">数据校验</h3></div>
            <div v-if="validationErrors.length" class="validate-block errors">
              <div v-for="e in validationErrors" :key="e" class="validate-line"><CircleAlert class="icon-sm" /><span>{{ e }}</span></div>
            </div>
            <div v-if="validationWarnings.length" class="validate-block warnings">
              <div v-for="w in validationWarnings" :key="w" class="validate-line"><CircleAlert class="icon-sm" /><span>{{ w }}</span></div>
            </div>
          </div>

          <div v-if="diffFields.length" class="card">
            <div class="card-header"><span class="eyebrow">差异</span><h3 class="card-title">双引擎差异字段</h3></div>
            <div v-for="f in diffFields" :key="f" class="diff-item">
              <div class="diff-field-name">{{ fieldLabel(f) }}</div>
              <div class="diff-compare">
                <div class="diff-cell"><span class="engine-label">智谱</span><span class="engine-val">{{ diffDetail[f]?.zhipu || '-' }}</span></div>
                <div class="diff-cell"><span class="engine-label">阿里云</span><span class="engine-val">{{ diffDetail[f]?.aliyun || '-' }}</span></div>
                <div class="diff-cell chosen"><span class="engine-label">采纳</span><span class="engine-val">{{ chosenLabel(diffDetail[f]?.chosen) }}</span></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 右栏：可编辑表单 -->
        <div class="right-col">
          <div class="card form-card">
            <div class="card-header"><span class="eyebrow">结果</span><h3 class="card-title">识别结果（可编辑修正）</h3></div>
            <div class="form-body">
              <div class="form-section">
                <div class="section-label">基础信息</div>
                <div class="field-row"><label>运单号</label><input v-model="form.waybill_no" :class="{ 'field-diff': isDiff('waybill_no') }" class="input-shell mono-text" /></div>
                <div class="field-row"><label>快递公司</label><input v-model="form.express_company" :class="{ 'field-diff': isDiff('express_company') }" class="input-shell" /></div>
                <div class="field-row"><label>托寄物</label><input v-model="form.goods" :class="{ 'field-diff': isDiff('goods') }" class="input-shell" /></div>
              </div>
              <div class="form-section">
                <div class="section-label">收件人信息</div>
                <div class="field-row"><label>姓名</label><input v-model="form.rec_name" :class="{ 'field-diff': isDiff('rec_name') }" class="input-shell" /></div>
                <div class="field-row"><label>电话</label><input v-model="form.rec_phone" :class="{ 'field-diff': isDiff('rec_phone') }" class="input-shell" /></div>
                <div class="field-row"><label>地址</label><textarea v-model="form.rec_address" :class="{ 'field-diff': isDiff('rec_address') }" class="input-shell textarea" rows="2"></textarea></div>
              </div>
              <div class="form-section">
                <div class="section-label">寄件人信息</div>
                <div class="field-row"><label>姓名</label><input v-model="form.sender_name" :class="{ 'field-diff': isDiff('sender_name') }" class="input-shell" /></div>
                <div class="field-row"><label>电话</label><input v-model="form.sender_phone" :class="{ 'field-diff': isDiff('sender_phone') }" class="input-shell" /></div>
                <div class="field-row"><label>地址</label><textarea v-model="form.sender_address" :class="{ 'field-diff': isDiff('sender_address') }" class="input-shell textarea" rows="2"></textarea></div>
              </div>
              <div class="form-section">
                <div class="section-label ai-label"><Sparkles class="icon-sm" /> AI分析</div>
                <div class="field-row"><label>退货原因</label><input v-model="form.return_reason" class="input-shell" /></div>
                <div class="field-row"><label>退货分类</label><input v-model="form.return_category" class="input-shell" /></div>
              </div>
            </div>
            <p class="form-note">双引擎交叉验证结果，请核对后确认。高亮字段为双引擎差异项，已自动采纳置信度更高的结果。</p>
          </div>
        </div>
      </div>

      <!-- 操作栏 -->
      <div class="action-bar">
        <button class="btn-ghost" @click="reset"><Refresh /><span>重新上传</span></button>
        <button class="btn-primary" :disabled="confirming" @click="handleConfirm">
          <Send v-if="!confirming" />
          <div v-else class="spinner-sm"></div>
          <span>{{ confirming ? '确认中...' : '确认写入飞书' }}</span>
        </button>
      </div>
    </div>

    <!-- ===== 状态5：批量结果列表 ===== -->
    <div v-else-if="state === 'batchResult'" class="batch-result-view">
      <div class="card">
        <div class="card-header">
          <div class="card-heading">
            <span class="eyebrow">批量</span>
            <h3 class="card-title">批量识别结果（{{ batchResults.length }} 张）</h3>
          </div>
          <div class="batch-summary">
            <span class="summary-pill ok">成功 {{ batchOkCount }}</span>
            <span class="summary-pill warn">待复核 {{ batchReviewCount }}</span>
          </div>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th style="width:40px"></th>
                <th>序号</th>
                <th>运单号</th>
                <th>快递公司</th>
                <th>收件人</th>
                <th>退货原因</th>
                <th>验证状态</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in batchResults" :key="i">
                <td><CircleCheck v-if="r.cross_validation === 'accept'" class="icon-done-sm" /><CircleAlert v-else class="icon-sm" /></td>
                <td>{{ i + 1 }}</td>
                <td class="mono-text">{{ r.waybill_no || r.waybillNo || '-' }}</td>
                <td>{{ r.express_company || r.expressCompany || '-' }}</td>
                <td>{{ r.rec_name || r.recName || '-' }}</td>
                <td>{{ r.return_reason || r.returnReason || '-' }}</td>
                <td><span :class="['table-pill', r.cross_validation === 'accept' ? 'ready' : 'review']">{{ r.cross_validation === 'accept' ? '一致' : '待复核' }}</span></td>
                <td><button class="btn-ghost btn-xs" @click="editBatchItem(i)"><Pen /><span>编辑</span></button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="action-bar">
        <button class="btn-ghost" @click="reset"><Refresh /><span>重新上传</span></button>
        <button class="btn-primary" :disabled="confirming" @click="handleBatchConfirm">
          <Send v-if="!confirming" />
          <div v-else class="spinner-sm"></div>
          <span>{{ confirming ? '批量确认中...' : '批量确认写入飞书' }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
// 步骤2：组件依赖与状态定义
import { ref, reactive, computed, watch, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import api from '../api';
import { CloudUpload, CircleCheck, CircleAlert, Send, Refresh, X, Image, Sparkles, Pen } from '../icons';

// 步骤2.1：接收 props（editRecord 来自退货记录页的跨页编辑）
const props = defineProps({
  editRecord: { type: Object, default: null },
});
const emit = defineEmits(['confirmed', 'navigate', 'clearEditRecord']);

// 模式与视图状态
const mode = ref('single'); // single | batch
const state = ref('idle'); // idle | processing | batchProcessing | result | batchResult
const dragOver = ref(false);
const isEditing = ref(false); // 标记当前是否为"编辑已有记录"模式

// SSE 处理步骤定义
const steps = ref([
  { step: 1, label: '上传至云存储', desc: '图片存储至腾讯云COS', status: 'pending', meta: '', subSteps: [] },
  { step: 2, label: '双引擎OCR并行识别', desc: '智谱OCR + 阿里云面单OCR 同时识别', status: 'pending', meta: '', subSteps: [
    { name: '智谱OCR', status: 'pending' },
    { name: '阿里云OCR', status: 'pending' },
  ] },
  { step: 3, label: '交叉验证 + 仲裁', desc: '双引擎结果比对，差异字段仲裁', status: 'pending', meta: '', subSteps: [] },
  { step: 4, label: 'DeepSeek 语义分析', desc: '退货原因提取 + 智能分类', status: 'pending', meta: '', subSteps: [] },
]);

let cancelFn = null;

// 结果与可编辑表单
const result = ref(null);
const form = reactive({
  waybill_no: '', express_company: '', goods: '',
  rec_name: '', rec_phone: '', rec_address: '',
  sender_name: '', sender_phone: '', sender_address: '',
  return_reason: '', return_category: '',
});

const confirming = ref(false);

// 批量结果
const batchResults = ref([]);

// 批量并行任务
const batchTasks = ref([]);

// 步骤3：计算属性
const currentStepNum = computed(() => {
  const idx = steps.value.findIndex(s => s.status === 'active');
  return idx >= 0 ? idx + 1 : steps.value.filter(s => s.status === 'done').length;
});
const currentStepLabel = computed(() => {
  const active = steps.value.find(s => s.status === 'active');
  if (active) return active.label + '中';
  const allDone = steps.value.every(s => s.status === 'done');
  return allDone ? '即将完成' : '准备中';
});

// 状态横幅
const statusLevel = computed(() => result.value?.cross_validation === 'accept' ? 'ok' : 'warn');
const statusTitle = computed(() => result.value?.cross_validation === 'accept' ? '识别成功' : '需人工复核');
const statusDesc = computed(() => result.value?.cross_validation === 'accept'
  ? '双引擎识别结果一致，已自动采用，请核对后确认'
  : '双引擎识别结果存在差异，已采纳置信度更高的结果，请重点核对高亮字段');
const confidenceLabel = computed(() => {
  const c = result.value?.confidence;
  if (c == null) return '中';
  if (c >= 0.9) return '高';
  if (c >= 0.7) return '中';
  return '低';
});
const sourceLabel = computed(() => {
  if (result.value?.diff_fields?.length) return '双引擎交叉';
  return result.value?.engine_source || '单引擎';
});

const validationErrors = computed(() => result.value?.validation_errors || []);
const validationWarnings = computed(() => result.value?.validation_warnings || []);
const diffFields = computed(() => result.value?.diff_fields || []);
const diffDetail = computed(() => result.value?.diff_detail || {});

const batchOkCount = computed(() => batchResults.value.filter(r => r.cross_validation === 'accept').length);
const batchReviewCount = computed(() => batchResults.value.filter(r => r.cross_validation !== 'accept').length);

// 批量处理进度
const batchDoneCount = computed(() => batchTasks.value.filter(t => t.overallStatus === 'done' || t.overallStatus === 'error').length);
const batchProgressPct = computed(() => {
  if (!batchTasks.value.length) return 0;
  return Math.round((batchDoneCount.value / batchTasks.value.length) * 100);
});

// 步骤4：组件挂载后检查是否有编辑记录传入（避免 immediate: true 在 TDZ 阶段触发）
onMounted(() => {
  if (props.editRecord) {
    loadEditRecord(props.editRecord);
    emit('clearEditRecord');
  }
});
// 额外 watch（无 immediate）：组件已挂载后 editRecord 变化时处理
watch(() => props.editRecord, (newVal) => {
  if (newVal) {
    loadEditRecord(newVal);
    emit('clearEditRecord');
  }
});

// 步骤5：方法
// 切换模式（处理中不允许切换，idle 态重置上传区，结果态保留数据）
function switchMode(m) {
  if (m === mode.value) return;
  // 处理中：阻止切换，避免丢失进度
  if (state.value === 'processing' || state.value === 'batchProcessing') {
    ElMessage.warning('正在处理中，请等待完成或取消后再切换');
    return;
  }
  mode.value = m;
  // idle 态切换模式时重置上传区
  if (state.value === 'idle') {
    reset();
  }
  // result/batchResult 态切换时保留当前结果
}

// 单张文件选择
function handleFileSelect(e) {
  const file = e.target.files?.[0];
  if (file) startUpload(file);
  e.target.value = ''; // 允许重复选择同一文件
}
function handleDrop(e) {
  const file = e.dataTransfer.files?.[0];
  if (file && file.type.startsWith('image/')) startUpload(file);
}
function handleBatchSelect(e) {
  const files = Array.from(e.target.files || []);
  if (files.length) startBatchUpload(files);
  e.target.value = '';
}
function handleBatchDrop(e) {
  const files = Array.from(e.dataTransfer.files || []).filter(f => f.type.startsWith('image/'));
  if (files.length) startBatchUpload(files);
}

// 重置步骤状态
function resetSteps() {
  steps.value = steps.value.map((s, i) => ({
    ...s,
    status: 'pending',
    meta: '',
    subSteps: (i === 1) ? [
      { name: '智谱OCR', status: 'pending' },
      { name: '阿里云OCR', status: 'pending' },
    ] : [],
  }));
}

// 启动单张 SSE 上传
function startUpload(file) {
  isEditing.value = false;
  resetSteps();
  result.value = null;
  state.value = 'processing';

  cancelFn = api.uploadSSE(
    file,
    (stepNum, label, status, subSteps) => {
      const idx = stepNum - 1;
      if (idx < 0 || idx >= steps.value.length) return;
      steps.value.forEach((s, i) => {
        if (i < idx) { s.status = 'done'; s.subSteps.forEach(ss => ss.status = 'done'); }
      });
      steps.value[idx].status = status;
      if (subSteps && subSteps.length) steps.value[idx].subSteps = subSteps;
    },
    (data) => {
      steps.value.forEach(s => { s.status = 'done'; s.subSteps.forEach(ss => ss.status = 'done'); });
      result.value = data;
      fillForm(data);
      state.value = 'result';
    },
    (msg) => {
      ElMessage.error(msg || '识别失败，请重试');
      reset();
    }
  );
}

// 取消上传
function cancelUpload() {
  if (cancelFn) { cancelFn(); cancelFn = null; }
  reset();
}

// 驼峰转下划线的字段映射表（后端返回驼峰，表单用下划线）
const camelToSnakeMap = {
  waybillNo: 'waybill_no', expressCompany: 'express_company',
  recName: 'rec_name', recPhone: 'rec_phone', recAddress: 'rec_address',
  senderName: 'sender_name', senderPhone: 'sender_phone', senderAddress: 'sender_address',
  returnReason: 'return_reason', returnCategory: 'return_category',
  goods: 'goods',
};

// 用识别结果填充表单（兼容驼峰和下划线两种字段名）
function fillForm(data) {
  const keys = ['waybill_no','express_company','goods','rec_name','rec_phone','rec_address','sender_name','sender_phone','sender_address','return_reason','return_category'];
  keys.forEach(k => {
    // 优先下划线，回退驼峰
    form[k] = data[k] ?? data[camelToSnakeReverse(k)] ?? '';
  });
}

// 下划线转驼峰（反向查找）
function camelToSnakeReverse(snake) {
  for (const [camel, s] of Object.entries(camelToSnakeMap)) {
    if (s === snake) return camel;
  }
  return snake;
}

// 加载编辑记录（从退货记录页跳转过来）
function loadEditRecord(row) {
  isEditing.value = true;
  mode.value = 'single';
  // 构造 result 对象（缺少的字段用默认值填充）
  result.value = {
    record_id: row.id,
    image_url: row.imageUrl || row.image_url || '',
    cross_validation: 'accept', // 编辑已有记录默认显示为"成功"
    confidence: 0.95,
    engine_source: row.ocrEngine || row.ocr_engine || '编辑模式',
    diff_fields: [],
    diff_detail: {},
    validation_errors: [],
    validation_warnings: [],
  };
  // 填充表单（row 是驼峰命名）
  fillForm(row);
  state.value = 'result';
}

function isDiff(field) {
  return diffFields.value.includes(field);
}
function fieldLabel(f) {
  const map = { rec_address: '收件地址', sender_address: '寄件地址', waybill_no: '运单号', rec_name: '收件人', rec_phone: '收件电话', sender_name: '寄件人', goods: '托寄物' };
  return map[f] || f;
}
function chosenLabel(chosen) {
  const map = { zhipu: '智谱', aliyun: '阿里云' };
  return map[chosen] || chosen || '-';
}

// 确认写入飞书（单张）
async function handleConfirm() {
  if (!result.value) return;
  confirming.value = true;
  try {
    const recordId = result.value.record_id || result.value.id;
    await api.confirm(recordId, { ...form });
    ElMessage.success('已写入飞书多维表格');
    emit('confirmed');
    reset();
  } catch (e) {
    ElMessage.error('写入失败：' + (e.response?.data?.msg || e.message || '网络错误'));
  } finally {
    confirming.value = false;
  }
}

// 批量确认写入飞书
async function handleBatchConfirm() {
  confirming.value = true;
  try {
    const ids = batchResults.value.map(r => r.record_id || r.id).filter(Boolean);
    if (!ids.length) {
      ElMessage.warning('暂无可确认的记录');
      return;
    }
    await api.batchConfirm(ids);
    ElMessage.success(`已批量写入飞书（${ids.length} 条）`);
    emit('confirmed');
    reset();
  } catch (e) {
    ElMessage.error('批量写入失败：' + (e.response?.data?.msg || e.message || '网络错误'));
  } finally {
    confirming.value = false;
  }
}

// 步骤6：批量上传（多任务并行 SSE）
function startBatchUpload(files) {
  isEditing.value = false;
  state.value = 'batchProcessing';
  const limitedFiles = files.slice(0, 20); // 最多20张

  // 初始化任务列表
  batchTasks.value = limitedFiles.map((f, i) => ({
    id: i,
    filename: f.name,
    file: f,
    overallStatus: 'pending', // pending | active | done | error
    errorMsg: '',
    cancelFn: null,
    steps: [
      { status: 'pending' }, // COS
      { status: 'pending' }, // OCR
      { status: 'pending' }, // 验证
      { status: 'pending' }, // LLM
    ],
    result: null,
  }));

  // 逐步启动每个文件的 SSE（限制并发数为3，避免后端压力过大）
  const MAX_CONCURRENT = 3;
  let started = 0;
  const startNext = () => {
    while (started < limitedFiles.length) {
      const pending = batchTasks.value.filter(t => t.overallStatus === 'pending');
      if (pending.length === 0 || pending.length > MAX_CONCURRENT) break;
      const task = pending[0];
      startBatchTask(task, startNext);
      started++;
    }
  };
  startNext();
}

// 启动单个批量任务的 SSE
function startBatchTask(task, onComplete) {
  task.overallStatus = 'active';

  task.cancelFn = api.uploadSSE(
    task.file,
    // 步骤回调
    (stepNum, label, status, subSteps) => {
      const idx = stepNum - 1;
      if (idx < 0 || idx >= task.steps.length) return;
      // 前置步骤标记完成
      task.steps.forEach((s, i) => { if (i < idx) s.status = 'done'; });
      task.steps[idx].status = status;
    },
    // 结果回调
    (data) => {
      task.steps.forEach(s => s.status = 'done');
      task.overallStatus = 'done';
      task.result = data;
      checkAllBatchDone();
      if (onComplete) onComplete();
    },
    // 错误回调
    (msg) => {
      task.overallStatus = 'error';
      task.errorMsg = msg;
      checkAllBatchDone();
      if (onComplete) onComplete();
    }
  );
}

// 检查所有批量任务是否完成
function checkAllBatchDone() {
  const allDone = batchTasks.value.every(t => t.overallStatus === 'done' || t.overallStatus === 'error');
  if (allDone) {
    // 将完成的任务结果提取到 batchResults
    batchResults.value = batchTasks.value
      .filter(t => t.overallStatus === 'done' && t.result)
      .map(t => t.result);
    state.value = 'batchResult';
  }
}

// 短步骤标签（批量处理卡片用）
function shortStepLabel(idx) {
  return ['上传', 'OCR', '验证', 'AI'][idx] || idx;
}

// 编辑批量结果中的某一项
function editBatchItem(idx) {
  isEditing.value = false;
  const r = batchResults.value[idx];
  result.value = { ...r, record_id: r.record_id || r.id };
  fillForm(r);
  mode.value = 'single';
  state.value = 'result';
}

// 重置到初始上传态
function reset() {
  // 取消所有进行中的 SSE
  if (cancelFn) { cancelFn(); cancelFn = null; }
  batchTasks.value.forEach(t => { if (t.cancelFn) t.cancelFn(); });
  state.value = 'idle';
  result.value = null;
  confirming.value = false;
  isEditing.value = false;
  batchResults.value = [];
  batchTasks.value = [];
  resetSteps();
}
</script>

<style scoped>
/* 步骤7：组件级样式 */
.screen-shell {
  flex: 1 0 auto;
  padding: calc(var(--spacing) * 4);
  display: grid;
  gap: calc(var(--spacing) * 4);
  align-content: start;
  min-width: 0;
  animation: screenIn .28s ease;
}
@keyframes screenIn { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }

/* 模式 Tab */
.mode-tabs {
  display: flex;
  gap: calc(var(--spacing) * 5);
  border-bottom: 1px solid var(--color-border);
  padding-bottom: calc(var(--spacing) * 2);
  min-width: 0;
}
.mode-tab {
  font: 500 13px/1 var(--font-sans);
  color: var(--color-fg-muted);
  padding-bottom: calc(var(--spacing) * 2);
  border: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  cursor: pointer;
  background: none;
  transition: var(--transition);
}
.mode-tab.active { color: var(--color-primary); border-bottom-color: var(--color-primary); }

/* 空闲上传区 */
.idle-view { display: grid; gap: calc(var(--spacing) * 4); }
.upload-zone {
  border: 2px dashed var(--color-border);
  border-radius: var(--radius);
  padding: 40px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: border-color .15s ease, background-color .15s ease;
  text-align: center;
}
.upload-zone:hover { border-color: var(--color-primary); }
.upload-zone.drag-active { border-color: var(--color-primary); background: var(--color-muted); }
.hidden-input { display: none; }
.upload-icon { color: var(--color-fg-muted); width: 48px; height: 48px; }
.upload-title { font-size: 14px; font-weight: 600; color: var(--color-fg); }
.upload-hint { font-size: 12px; color: var(--color-fg-muted); }

/* 处理中 */
.processing-view { display: grid; gap: calc(var(--spacing) * 4); }
.processing-card {
  max-width: 560px;
  margin: 0 auto;
  padding: calc(var(--spacing) * 6);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-card);
  display: flex;
  flex-direction: column;
  gap: calc(var(--spacing) * 4);
}
.processing-header { display: flex; align-items: center; gap: calc(var(--spacing) * 3); }
.processing-title { font: 600 16px/1 var(--font-sans); color: var(--color-fg); }
.processing-subtitle { font-size: 13px; color: var(--color-fg-muted); margin-top: 4px; }

/* Spinner */
@keyframes spin { to { transform: rotate(360deg); } }
.spinner-lg { width: 20px; height: 20px; border: 2px solid var(--color-border); border-top-color: var(--color-primary); border-radius: 50%; animation: spin .6s linear infinite; flex: none; }
.spinner-sm { width: 14px; height: 14px; border: 2px solid var(--color-border); border-top-color: var(--color-primary); border-radius: 50%; animation: spin .6s linear infinite; flex: none; }

/* 步骤时间线 */
.step-timeline { display: flex; flex-direction: column; position: relative; }
.step-row { display: flex; align-items: flex-start; gap: calc(var(--spacing) * 3); padding: calc(var(--spacing) * 2.5) 0; position: relative; }
.step-icon { width: 24px; height: 24px; flex: none; display: grid; place-items: center; position: relative; z-index: 1; }
.icon-done { color: var(--color-success-strong); width: 20px; height: 20px; }
.icon-done-sm { color: var(--color-success-strong); width: 14px; height: 14px; }
.circle-pending, .circle-pending-sm { border: 2px solid var(--color-border); border-radius: 50%; background: var(--color-card); }
.circle-pending { width: 20px; height: 20px; }
.circle-pending-sm { width: 14px; height: 14px; }
.step-connector { position: absolute; left: 11px; top: 24px; width: 2px; height: calc(100% - 24px); background: var(--color-border); }
.step-connector.done { background: var(--color-success-strong); }
.step-content { flex: 1; min-width: 0; }
.step-label { font-size: 13px; font-weight: 600; color: var(--color-fg); }
.step-label.pending { font-weight: 500; color: var(--color-fg-muted); }
.step-label.active { color: var(--color-primary); }
.step-desc { font-size: 12px; color: var(--color-fg-muted); margin-top: 2px; }
.step-meta { font-size: 11px; font-family: var(--font-mono); color: var(--color-fg-muted); margin-top: 4px; }
.sub-steps { display: flex; flex-direction: column; gap: 6px; margin-top: 8px; padding-left: 28px; }
.sub-step { display: flex; align-items: center; gap: 8px; font-size: 12px; }

.processing-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: calc(var(--spacing) * 4);
  border-top: 1px solid var(--color-border);
}
.step-progress { font: 500 12px/1 var(--font-mono); color: var(--color-fg-muted); }
.tech-note {
  max-width: 560px;
  margin: 0 auto;
  padding: calc(var(--spacing) * 3);
  background: var(--color-muted);
  border-radius: var(--radius);
  font: 400 11px/1.5 var(--font-mono);
  color: var(--color-fg-muted);
  text-align: center;
}

/* ===== 批量处理中（多任务并行） ===== */
.batch-processing-view { display: grid; gap: calc(var(--spacing) * 4); }
.bp-header {
  display: flex;
  align-items: center;
  gap: calc(var(--spacing) * 3);
  padding: calc(var(--spacing) * 4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-card);
}
.bp-progress-bar {
  flex: 1;
  height: 6px;
  background: var(--color-muted);
  border-radius: 3px;
  overflow: hidden;
  min-width: 120px;
}
.bp-progress-fill {
  height: 100%;
  background: var(--color-primary);
  border-radius: 3px;
  transition: width 0.3s ease;
}
.bp-task-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: calc(var(--spacing) * 3);
}
.bp-task-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-card);
  padding: calc(var(--spacing) * 3);
  display: flex;
  flex-direction: column;
  gap: calc(var(--spacing) * 2);
}
.bp-task-card.done { border-color: var(--color-success-subtle); }
.bp-task-card.error { border-color: var(--color-error-subtle); background: var(--color-error-subtle); }
.bp-task-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.bp-task-name {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-fg);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}
.bp-mini-steps {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.bp-mini-step {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--color-fg-muted);
}
.bp-mini-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1.5px solid var(--color-border);
  background: var(--color-card);
  flex: none;
}
.bp-mini-step.active .bp-mini-dot { border-color: var(--color-primary); background: var(--color-primary); }
.bp-mini-step.done .bp-mini-dot { border-color: var(--color-success-strong); background: var(--color-success-strong); }
.bp-mini-step.active .bp-mini-label { color: var(--color-primary); font-weight: 500; }
.bp-mini-step.done .bp-mini-label { color: var(--color-success-strong); }
.bp-task-error { font-size: 11px; color: var(--color-error-strong); margin-top: 4px; }

/* 编辑来源提示 */
.edit-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: calc(var(--spacing) * 2) calc(var(--spacing) * 3);
  background: var(--color-muted);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 12px;
  color: var(--color-fg-muted);
}
.edit-banner .banner-icon { width: 14px; height: 14px; }

/* 结果视图 */
.result-view { display: grid; gap: calc(var(--spacing) * 4); align-content: start; }
.status-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: calc(var(--spacing) * 3);
  padding: calc(var(--spacing) * 3) calc(var(--spacing) * 4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  flex-wrap: wrap;
}
.status-banner.ok { background: var(--color-success-subtle); border-color: var(--color-success-subtle); }
.status-banner.warn { background: var(--color-error-subtle); border-color: var(--color-error-subtle); }
.banner-icon { width: 20px; height: 20px; flex: none; }
.status-banner.ok .banner-icon { color: var(--color-success-strong); }
.status-banner.warn .banner-icon { color: var(--color-error-strong); }
.banner-text { flex: 1; min-width: 0; }
.banner-title { font-size: 13px; font-weight: 600; margin: 0; }
.status-banner.ok .banner-title { color: var(--color-success-strong); }
.status-banner.warn .banner-title { color: var(--color-error-strong); }
.banner-desc { font-size: 12px; margin: 2px 0 0; }
.status-banner.ok .banner-desc { color: var(--color-success-strong); opacity: 0.85; }
.status-banner.warn .banner-desc { color: var(--color-error-strong); opacity: 0.85; }
.banner-meta { display: flex; align-items: center; gap: calc(var(--spacing) * 4); font-size: 12px; flex: none; white-space: nowrap; }
.status-banner.ok .banner-meta { color: var(--color-success-strong); }
.status-banner.warn .banner-meta { color: var(--color-error-strong); }
.meta-item b { font-weight: 600; }

/* 双栏面板 */
.panel-grid { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(300px, 0.95fr); gap: calc(var(--spacing) * 3); align-items: start; }
.left-col, .right-col { display: flex; flex-direction: column; gap: calc(var(--spacing) * 3); min-width: 0; }

/* 卡片 */
.card {
  display: flex;
  flex-direction: column;
  gap: calc(var(--spacing) * 3);
  padding: calc(var(--spacing) * 3.5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-card);
  min-width: 0;
}
.card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: calc(var(--spacing) * 3); min-width: 0; }
.card-heading { flex: 1; min-width: 0; }
.eyebrow { font: 500 10.5px/1 var(--font-mono); text-transform: uppercase; letter-spacing: 0.06em; color: var(--color-fg-muted); }
.card-title { margin: 4px 0 0; font: 600 14px/1.3 var(--font-sans); }

/* 原图预览 */
.image-preview { height: 240px; border-radius: var(--radius); overflow: hidden; background: var(--color-muted); }
.waybill-img { width: 100%; height: 100%; object-fit: cover; }
.image-placeholder { height: 240px; background: var(--color-muted); border-radius: var(--radius); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: var(--color-fg-muted); }
.placeholder-icon { width: 32px; height: 32px; }
.image-meta { font-family: var(--font-mono); font-size: 13px; color: var(--color-fg); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* 校验 */
.validate-block { display: flex; flex-direction: column; gap: 6px; }
.validate-line { display: flex; align-items: center; gap: 8px; font-size: 13px; }
.validate-block.errors .validate-line { color: var(--color-error-strong); }
.validate-block.warnings .validate-line { color: var(--color-fg); }
.icon-sm { width: 16px; height: 16px; flex: none; }

/* 差异字段 */
.diff-item { display: flex; flex-direction: column; gap: calc(var(--spacing) * 2); padding-bottom: calc(var(--spacing) * 3); border-bottom: 1px solid var(--color-border); }
.diff-item:last-child { border-bottom: none; padding-bottom: 0; }
.diff-field-name { font-size: 13px; font-weight: 600; color: var(--color-fg); }
.diff-compare { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: calc(var(--spacing) * 2); }
.diff-cell { background: var(--color-muted); padding: calc(var(--spacing) * 2) calc(var(--spacing) * 2.5); border-radius: var(--radius); display: flex; flex-direction: column; gap: 6px; }
.diff-cell.chosen { border: 1px solid var(--color-chart-3); }
.engine-label { font: 500 11px/1 var(--font-mono); color: var(--color-fg-muted); }
.engine-val { font-size: 12px; color: var(--color-fg-muted); }
.diff-cell.chosen .engine-val { color: var(--color-primary); font-weight: 600; }

/* 表单 */
.form-card { gap: calc(var(--spacing) * 3); }
.form-body { display: flex; flex-direction: column; gap: calc(var(--spacing) * 4); }
.form-section { display: grid; gap: calc(var(--spacing) * 2); }
.section-label { font: 500 11px/1 var(--font-mono); letter-spacing: 0.06em; color: var(--color-fg-muted); display: flex; align-items: center; gap: 6px; }
.ai-label { display: inline-flex; align-items: center; gap: 6px; }
.field-row { display: grid; gap: 6px; }
.field-row label { font-size: 12px; color: var(--color-fg-muted); }
.input-shell {
  height: 34px;
  display: flex;
  align-items: center;
  padding: 0 calc(var(--spacing) * 3);
  border: 1px solid var(--color-input);
  border-radius: var(--radius);
  background: var(--color-bg);
  font-size: 13px;
  color: var(--color-fg);
  font-family: var(--font-sans);
  width: 100%;
  box-sizing: border-box;
}
input.input-shell { height: 34px; }
textarea.input-shell { height: auto; min-height: 60px; padding: calc(var(--spacing) * 2) calc(var(--spacing) * 3); line-height: 1.5; resize: vertical; }
.mono-text { font-family: var(--font-mono); }
.field-diff { border-color: var(--color-chart-3); }
.form-note { color: var(--color-fg-muted); font-size: 12px; }

/* 操作栏 */
.action-bar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: calc(var(--spacing) * 2);
  padding: calc(var(--spacing) * 3.5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-card);
}
.btn-primary, .btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: calc(var(--spacing) * 2);
  height: 34px;
  padding: 0 calc(var(--spacing) * 3);
  border-radius: var(--radius);
  cursor: pointer;
  font: 500 13px/1 var(--font-sans);
  white-space: nowrap;
  transition: var(--transition);
  border: 1px solid transparent;
}
.btn-primary { background: var(--color-primary); color: var(--color-primary-fg); }
.btn-primary:hover:not(:disabled) { background: #000; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-ghost { background: transparent; color: var(--color-fg); border-color: var(--color-border); }
.btn-ghost:hover { background: var(--color-accent); }
.btn-xs { height: 26px; padding: 0 calc(var(--spacing) * 2); font-size: 12px; }

/* 批量结果 */
.batch-result-view { display: grid; gap: calc(var(--spacing) * 4); align-content: start; }
.batch-summary { display: flex; gap: calc(var(--spacing) * 2); }
.summary-pill { padding: 2px calc(var(--spacing) * 2); border-radius: 999px; font-size: 11.5px; font-weight: 500; }
.summary-pill.ok { background: var(--color-success-subtle); color: var(--color-success-strong); }
.summary-pill.warn { background: var(--color-primary); color: var(--color-primary-fg); }
.table-wrap { min-width: 0; overflow-x: auto; }
.data-table { width: 100%; min-width: 620px; border-collapse: collapse; table-layout: fixed; }
.data-table th, .data-table td { padding: calc(var(--spacing) * 2.5) calc(var(--spacing) * 3); border-bottom: 1px solid var(--color-border); text-align: left; font-size: 12.5px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; vertical-align: middle; }
.data-table th { font: 500 10.5px/1 var(--font-mono); text-transform: uppercase; letter-spacing: 0.06em; color: var(--color-fg-muted); }
.data-table tr:last-child td { border-bottom: none; }
.table-pill { padding: 2px calc(var(--spacing) * 2); border-radius: 999px; font-size: 11.5px; font-weight: 500; display: inline-flex; align-items: center; justify-content: center; }
.table-pill.ready { background: var(--color-success-subtle); color: var(--color-success-strong); }
.table-pill.review { background: var(--color-primary); color: var(--color-primary-fg); }

/* 响应式 */
@media (max-width: 1080px) {
  .panel-grid { grid-template-columns: 1fr; }
  .diff-compare { grid-template-columns: 1fr; }
}
@media (max-width: 640px) {
  .screen-shell { padding: calc(var(--spacing) * 3); }
  .processing-card { padding: calc(var(--spacing) * 4); }
  .status-banner { flex-direction: column; align-items: flex-start; }
  .action-bar { flex-direction: column; }
  .action-bar .btn-primary, .action-bar .btn-ghost { width: 100%; justify-content: center; }
  .data-table { min-width: 540px; }
  .bp-task-grid { grid-template-columns: 1fr; }
}
</style>