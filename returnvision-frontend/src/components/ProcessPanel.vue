<template>
  <div class="process-panel">
    <!-- ===== 空状态 ===== -->
    <div v-if="!result" class="empty-state">
      <div class="empty-icon">
        <el-icon :size="48"><Timer /></el-icon>
      </div>
      <p class="empty-text">请先在主页上传快递面单</p>
    </div>

    <!-- ===== 时间线 + 结果摘要 ===== -->
    <template v-else>
      <!-- 标题栏 -->
      <div class="panel-header">
        <div class="header-left">
          <el-icon :size="20" color="#0d9488"><Timer /></el-icon>
          <span class="header-title">处理流程</span>
          <el-tag type="primary" effect="light" round size="small">
            记录 #{{ result.record_id }}
          </el-tag>
        </div>
        <div class="header-right">
          <el-image
            v-if="result.image_url"
            :src="result.image_url"
            fit="cover"
            :preview-src-list="[result.image_url]"
            preview-teleported
            class="header-thumb"
          />
        </div>
      </div>

      <!-- 竖向时间线 -->
      <div class="timeline-card">
        <div class="timeline">
          <div
            v-for="(step, index) in steps"
            :key="step.id"
            class="timeline-step"
            :class="step.status"
          >
            <!-- 左侧状态圆圈 -->
            <div class="step-circle">
              <el-icon v-if="step.status === 'completed'" :size="16"><Check /></el-icon>
              <span v-else class="step-emoji">{{ step.emoji }}</span>
            </div>

            <!-- 右侧内容 -->
            <div class="step-content">
              <div class="step-header">
                <span class="step-title">{{ step.title }}</span>
                <el-tag
                  :type="statusTagType(step.status)"
                  effect="light"
                  size="small"
                  round
                >
                  {{ statusLabel(step.status) }}
                </el-tag>
                <span v-if="step.status === 'completed' && step.duration" class="step-duration">
                  耗时 {{ step.duration }}
                </span>
              </div>
              <p v-if="step.detail" class="step-detail">{{ step.detail }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 识别结果摘要 -->
      <div class="summary-card">
        <div class="card-label">
          <el-icon color="#0d9488"><Document /></el-icon>
          <span>识别结果摘要</span>
        </div>

        <!-- 关键字段网格 -->
        <div class="summary-grid">
          <div v-for="field in summaryFields" :key="field.key" class="summary-item">
            <span class="summary-label">{{ field.label }}</span>
            <span class="summary-value" :class="{ 'field-diff': isDiffField(field.key) }">
              {{ field.value || '-' }}
            </span>
          </div>
        </div>

        <!-- 交叉验证状态 -->
        <div class="summary-section">
          <div class="section-label">
            <el-icon color="#0d9488"><Connection /></el-icon>
            <span>交叉验证</span>
          </div>
          <div class="section-tags">
            <el-tag :type="validationTagType(result.cross_validation)" effect="light" round>
              {{ validationLabel(result.cross_validation) }}
            </el-tag>
            <el-tag :type="confidenceTagType(result.confidence)" effect="plain" round>
              置信度：{{ confidenceLabel(result.confidence) }}
            </el-tag>
            <el-tag v-if="result.source" type="info" effect="plain" round size="small">
              引擎：{{ result.source }}
            </el-tag>
          </div>
        </div>

        <!-- DeepSeek 分析结果 -->
        <div v-if="result.return_reason || result.return_category" class="summary-section">
          <div class="section-label">
            <el-icon color="#0d9488"><MagicStick /></el-icon>
            <span>DeepSeek 智能分析</span>
          </div>
          <div class="analysis-row">
            <div class="analysis-item">
              <span class="analysis-key">退货原因</span>
              <span class="analysis-val">{{ result.return_reason || '-' }}</span>
            </div>
            <div class="analysis-item">
              <span class="analysis-key">退货分类</span>
              <el-tag type="warning" effect="light" round size="large">
                {{ result.return_category || '-' }}
              </el-tag>
            </div>
          </div>
        </div>

        <!-- 差异字段提示 -->
        <div v-if="result.diff_fields && result.diff_fields.length > 0" class="diff-notice">
          <el-icon color="#f59e0b"><WarningFilled /></el-icon>
          <span>以下字段双引擎识别存在差异：{{ result.diff_fields.map(fieldLabel).join('、') }}</span>
        </div>

        <!-- 数据校验警告 -->
        <div v-if="validationWarnings.length > 0" class="validation-notice">
          <div class="validation-header">
            <el-icon :color="result.validation?.passed ? '#10b981' : '#ef4444'"><CircleCheck /></el-icon>
            <span>数据校验：{{ result.validation?.passed ? '通过' : '不通过' }}</span>
          </div>
          <div class="warning-list">
            <div v-for="(w, i) in validationWarnings" :key="i" class="warning-item">
              <el-icon color="#f59e0b"><WarningFilled /></el-icon>
              <span>{{ w }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-bar">
        <el-button
          type="primary"
          size="large"
          round
          :loading="confirming"
          :disabled="!animationDone"
          @click="handleConfirm"
        >
          <el-icon v-if="!confirming"><Check /></el-icon>
          确认写入飞书
        </el-button>
        <el-button size="large" round plain @click="handleReset">
          <el-icon><RefreshLeft /></el-icon>
          重新上传
        </el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import {
  Timer, Check, Document, Connection, MagicStick,
  WarningFilled, CircleCheck, RefreshLeft,
} from '@element-plus/icons-vue';
import api from '../api';

const props = defineProps({
  result: { type: Object, default: null },
});
const emit = defineEmits(['confirmed', 'reset']);

// 字段中文名映射
const FIELD_LABELS = {
  waybill_no: '运单号',
  express_company: '快递公司',
  rec_name: '收件人',
  rec_phone: '收件电话',
  rec_address: '收件地址',
  sender_name: '寄件人',
  sender_phone: '寄件电话',
  sender_address: '寄件地址',
  goods: '托寄物',
};

// 模拟耗时（展示用）
const MOCK_DURATIONS = ['0.3s', '1.2s', '1.1s', '0.4s', '2.1s', '0.2s', '0.1s', '-'];

// 时间线步骤定义
const steps = ref([
  { id: 1, emoji: '📤', title: '图片上传COS', status: 'pending', detail: '', duration: '' },
  { id: 2, emoji: '🔍', title: '智谱OCR识别', status: 'pending', detail: '', duration: '' },
  { id: 3, emoji: '🔍', title: '阿里云OCR识别', status: 'pending', detail: '', duration: '' },
  { id: 4, emoji: '✅', title: '交叉验证', status: 'pending', detail: '', duration: '' },
  { id: 5, emoji: '🤖', title: 'DeepSeek分析', status: 'pending', detail: '', duration: '' },
  { id: 6, emoji: '📋', title: '数据校验', status: 'pending', detail: '', duration: '' },
  { id: 7, emoji: '💾', title: '数据存储', status: 'pending', detail: '', duration: '' },
  { id: 8, emoji: '⏳', title: '等待确认', status: 'pending', detail: '', duration: '' },
]);

const confirming = ref(false);
const animationDone = ref(false);
let timeouts = [];

// 校验警告列表
const validationWarnings = computed(() => {
  return props.result?.validation?.warnings || [];
});

// 摘要展示字段
const summaryFields = computed(() => {
  const d = props.result?.data || {};
  return [
    { key: 'waybill_no', label: '运单号', value: d.waybill_no },
    { key: 'express_company', label: '快递公司', value: d.express_company },
    { key: 'rec_name', label: '收件人', value: d.rec_name },
    { key: 'rec_phone', label: '收件电话', value: d.rec_phone },
    { key: 'rec_address', label: '收件地址', value: d.rec_address },
    { key: 'sender_name', label: '寄件人', value: d.sender_name },
    { key: 'goods', label: '托寄物', value: d.goods },
  ];
});

// 判断字段是否在差异列表中
const isDiffField = (key) => {
  return props.result?.diff_fields?.includes(key);
};

const fieldLabel = (f) => FIELD_LABELS[f] || f;

// 状态标签映射
const statusTagType = (s) => {
  const map = { completed: 'success', active: 'primary', pending: 'info' };
  return map[s] || 'info';
};

const statusLabel = (s) => {
  const map = { completed: '已完成', active: '进行中', pending: '待处理' };
  return map[s] || s;
};

// 交叉验证状态映射
const validationTagType = (val) => {
  const map = { accept: 'success', review: 'warning', manual: 'danger' };
  return map[val] || 'info';
};

const validationLabel = (val) => {
  const map = { accept: '自动采用', review: '需复核', manual: '转人工' };
  return map[val] || val || '-';
};

// 置信度映射
const confidenceTagType = (c) => {
  const map = { high: 'success', medium: 'warning', low: 'danger' };
  return map[c] || 'info';
};

const confidenceLabel = (c) => {
  const map = { high: '高', medium: '中', low: '低' };
  return map[c] || c || '-';
};

// 用真实数据填充各步骤详情
const prepareDetails = (data) => {
  const d = data.data || {};
  const cvLabel = { accept: '自动采用', review: '需复核', manual: '转人工' };
  const confLabel = { high: '高', medium: '中', low: '低' };
  const warnings = data.validation?.warnings || [];

  // 计算识别文本长度（所有字段值拼接）
  const textLen = Object.values(d).filter((v) => v).join('').length;

  const details = [
    '图片已上传至腾讯云COS',
    `识别文本长度: ${textLen} 字符`,
    '面单信息识别完成',
    `${cvLabel[data.cross_validation] || data.cross_validation || '-'} · 置信度: ${confLabel[data.confidence] || data.confidence || '-'}`,
    data.return_reason
      ? `退货原因: ${data.return_reason} · 分类: ${data.return_category || '-'}`
      : '分析完成',
    `${data.validation?.passed ? '校验通过' : '校验不通过'} · ${warnings.length} 条警告`,
    `记录ID: #${data.record_id}`,
    '等待用户确认写入飞书',
  ];

  steps.value.forEach((step, i) => {
    step.detail = details[i];
    step.duration = MOCK_DURATIONS[i];
  });
};

// 启动进度模拟动画
const startAnimation = () => {
  // 清理之前的定时器
  timeouts.forEach(clearTimeout);
  timeouts = [];

  // 重置所有步骤
  steps.value.forEach((s) => { s.status = 'pending'; });
  animationDone.value = false;

  const stepDelay = 500;

  // 逐步将前7步变为 active -> completed
  for (let i = 0; i < 7; i++) {
    // 步骤变为进行中
    timeouts.push(setTimeout(() => {
      steps.value[i].status = 'active';
    }, i * stepDelay));

    // 步骤变为完成
    timeouts.push(setTimeout(() => {
      steps.value[i].status = 'completed';
    }, (i + 1) * stepDelay));
  }

  // 最后一步变为进行中（等待确认）
  timeouts.push(setTimeout(() => {
    steps.value[7].status = 'active';
    animationDone.value = true;
  }, 7 * stepDelay));
};

// 监听 result 变化，准备数据并启动动画
watch(
  () => props.result,
  (newVal) => {
    if (newVal) {
      prepareDetails(newVal);
      startAnimation();
    }
  },
  { immediate: true }
);

// 确认写入飞书
const handleConfirm = async () => {
  confirming.value = true;
  try {
    const res = await api.confirm(props.result.record_id, props.result.data);
    if (res.data.code === 0) {
      emit('confirmed');
    } else {
      ElMessage.error(res.data.msg || '写入失败');
    }
  } catch (err) {
    ElMessage.error('确认失败：' + (err.response?.data?.msg || err.message));
  } finally {
    confirming.value = false;
  }
};

// 重新上传
const handleReset = () => {
  emit('reset');
};

// 组件卸载时清理定时器
onUnmounted(() => {
  timeouts.forEach(clearTimeout);
});
</script>

<style scoped>
.process-panel { width: 100%; }

/* ===== 空状态 ===== */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100px 20px;
  background: #fff;
  border-radius: var(--rv-card-radius);
  box-shadow: var(--rv-shadow);
}
.empty-icon {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  color: #94a3b8;
}
.empty-text {
  font-size: 15px;
  color: #94a3b8;
}

/* ===== 标题栏 ===== */
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: var(--rv-card-radius);
  box-shadow: var(--rv-shadow);
  padding: 14px 20px;
  margin-bottom: 16px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
.header-thumb {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  border: 1px solid #e8efee;
  cursor: pointer;
}

/* ===== 时间线卡片 ===== */
.timeline-card {
  background: #fff;
  border-radius: var(--rv-card-radius);
  box-shadow: var(--rv-shadow);
  padding: 28px 24px;
  margin-bottom: 16px;
}

.timeline {
  display: flex;
  flex-direction: column;
}

.timeline-step {
  display: flex;
  gap: 16px;
  position: relative;
  padding-bottom: 22px;
}
.timeline-step:last-child {
  padding-bottom: 0;
}

/* 步骤间竖线 */
.timeline-step:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 16px;
  top: 36px;
  bottom: 0;
  width: 2px;
  background: #e2e8f0;
  transition: background 0.4s ease;
}
.timeline-step.completed:not(:last-child)::before {
  background: #0d9488;
}

/* 状态圆圈 */
.step-circle {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  z-index: 1;
  transition: all 0.3s ease;
  background: #f1f5f9;
  color: #cbd5e1;
}

/* 完成：teal实心 + 白色打勾 */
.timeline-step.completed .step-circle {
  background: #0d9488;
  color: #fff;
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.3);
}

/* 进行中：teal边框 + 脉冲动画 */
.timeline-step.active .step-circle {
  background: #fff;
  border: 2px solid #0d9488;
  color: #0d9488;
  animation: pulse 1.5s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(13, 148, 136, 0.4);
  }
  50% {
    box-shadow: 0 0 0 8px rgba(13, 148, 136, 0);
  }
}

.step-emoji {
  font-size: 15px;
  line-height: 1;
}

/* 步骤内容 */
.step-content {
  flex: 1;
  padding-top: 4px;
}
.step-header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 4px;
}
.step-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}
.timeline-step.pending .step-title {
  color: #94a3b8;
}
.step-duration {
  font-size: 12px;
  color: #94a3b8;
  margin-left: auto;
}
.step-detail {
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}
.timeline-step.pending .step-detail {
  color: #cbd5e1;
}

/* ===== 结果摘要卡片 ===== */
.summary-card {
  background: #fff;
  border-radius: var(--rv-card-radius);
  box-shadow: var(--rv-shadow);
  padding: 18px 20px;
  margin-bottom: 16px;
  transition: box-shadow 0.25s ease;
}
.summary-card:hover {
  box-shadow: var(--rv-shadow-hover);
}

.card-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 14px;
}
.card-label .el-icon { font-size: 17px; color: #0d9488; }

/* 字段网格 */
.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px 20px;
  margin-bottom: 18px;
}
.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.summary-label {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 500;
}
.summary-value {
  font-size: 14px;
  color: #1f2937;
  font-weight: 500;
  word-break: break-all;
}
.summary-value.field-diff {
  color: #f59e0b;
}

/* 分区标题 */
.summary-section {
  padding-top: 14px;
  border-top: 1px solid #f0f5f4;
  margin-bottom: 14px;
}
.summary-section:last-child {
  margin-bottom: 0;
}
.section-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 10px;
}
.section-label .el-icon { font-size: 15px; color: #0d9488; }

.section-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

/* DeepSeek 分析 */
.analysis-row {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}
.analysis-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
  min-width: 200px;
}
.analysis-key {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 500;
}
.analysis-val {
  font-size: 15px;
  color: #1f2937;
  font-weight: 600;
}

/* 差异字段提示 */
.diff-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fffbeb;
  border-radius: 8px;
  padding: 10px 14px;
  font-size: 13px;
  color: #92400e;
  font-weight: 500;
  margin-bottom: 14px;
}
.diff-notice .el-icon { font-size: 16px; flex-shrink: 0; }

/* 校验警告 */
.validation-notice {
  background: #fffbeb;
  border-radius: 8px;
  padding: 12px 14px;
}
.validation-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #78716c;
  margin-bottom: 8px;
}
.warning-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.warning-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #78716c;
  background: #fff;
  border-radius: 6px;
  padding: 6px 10px;
}
.warning-item .el-icon { font-size: 14px; flex-shrink: 0; }

/* ===== 操作按钮 ===== */
.action-bar {
  display: flex;
  gap: 12px;
  justify-content: center;
  padding: 8px 0 4px;
}
.action-bar .el-button { min-width: 150px; }

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .summary-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .step-duration {
    margin-left: 0;
  }
}

@media (max-width: 500px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
