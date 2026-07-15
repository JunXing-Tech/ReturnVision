<template>
  <div class="home-panel">
    <!-- ===== Hero 区域 ===== -->
    <section class="hero-section">
      <div class="hero-badge">
        <el-icon><Promotion /></el-icon>
        <span>ReturnVision</span>
      </div>
      <h1 class="hero-title">退运智录</h1>
      <p class="hero-subtitle">
        拍照识别快递面单 -&gt; 双引擎交叉验证 -&gt; DeepSeek分析 -&gt; 飞书写入
      </p>
    </section>

    <!-- ===== 特性卡片网格 ===== -->
    <section class="features-section">
      <div class="features-grid">
        <div
          v-for="feature in features"
          :key="feature.title"
          class="feature-card"
        >
          <div class="feature-icon-wrapper">
            <el-icon :size="22"><component :is="feature.icon" /></el-icon>
          </div>
          <div class="feature-content">
            <h3 class="feature-title">{{ feature.title }}</h3>
            <p class="feature-desc">{{ feature.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ===== 上传区域（无结果时显示） ===== -->
    <section v-if="!result" class="upload-section">
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="true"
        :show-file-list="false"
        :http-request="handleUpload"
        accept="image/*"
        class="upload-zone"
        :disabled="loading"
      >
        <div v-if="!loading" class="upload-inner">
          <div class="upload-icon-circle">
            <el-icon :size="36"><UploadFilled /></el-icon>
          </div>
          <h2 class="upload-title">上传快递面单图片</h2>
          <p class="upload-desc">拖拽图片到此处，或<em>点击选择文件</em></p>
          <div class="upload-hints">
            <span class="hint-chip"><el-icon><Picture /></el-icon> 支持 JPG / PNG</span>
            <span class="hint-chip"><el-icon><Document /></el-icon> 单文件 ≤ 10MB</span>
            <span class="hint-chip"><el-icon><MagicStick /></el-icon> 双引擎 OCR + AI 分析</span>
          </div>
        </div>
        <div v-else class="loading-inner">
          <div class="loading-spinner">
            <span class="spinner-ring"></span>
            <el-icon class="spinner-icon" :size="28"><Loading /></el-icon>
          </div>
          <h3 class="loading-title">正在智能识别中</h3>
          <p class="loading-step">双引擎 OCR 并行识别 -&gt; 交叉验证 -&gt; DeepSeek 分析</p>
          <div class="loading-progress">
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
          </div>
        </div>
      </el-upload>

      <!-- 查看记录入口 -->
      <div class="upload-extra">
        <el-button text type="primary" @click="goToRecords">
          <el-icon><Document /></el-icon>
          查看记录列表
        </el-button>
      </div>
    </section>

    <!-- ===== 结果展示区（上传成功后内联显示） ===== -->
    <section v-else ref="resultSectionRef" class="result-section">
      <!-- 状态栏 -->
      <div class="result-status-bar">
        <div class="status-bar-left">
          <el-icon :size="18" color="var(--color-primary)"><CircleCheck /></el-icon>
          <span class="status-title">识别结果</span>
          <el-tag type="info" effect="plain" round size="small">
            #{{ result.record_id }}
          </el-tag>
        </div>
        <div class="status-bar-right">
          <el-tag :type="validationTagType(result.cross_validation)" effect="light" round>
            {{ validationLabel(result.cross_validation) }}
          </el-tag>
          <el-tag :type="confidenceTagType(result.confidence)" effect="plain" round>
            置信度：{{ confidenceLabel(result.confidence) }}
          </el-tag>
        </div>
      </div>

      <!-- 两列布局：图片 + 表单 -->
      <el-row :gutter="20" class="result-body">
        <!-- 左列：图片预览 -->
        <el-col :xs="24" :md="8">
          <div class="image-preview-card">
            <el-image
              v-if="result.image_url"
              :src="result.image_url"
              fit="contain"
              :preview-src-list="[result.image_url]"
              preview-teleported
              class="preview-image"
            />
            <div v-else class="no-image">
              <el-icon :size="32"><Picture /></el-icon>
              <span>暂无图片</span>
            </div>
          </div>
        </el-col>

        <!-- 右列：表单 + 分析 + 操作 -->
        <el-col :xs="24" :md="16">
          <!-- 可编辑表单 -->
          <div class="form-card">
            <div class="card-header">
              <el-icon color="var(--color-primary)"><Document /></el-icon>
              <span>面单信息（可编辑）</span>
            </div>
            <el-form :model="formData" label-position="top" class="result-form">
              <el-row :gutter="16">
                <el-col
                  v-for="field in formFields"
                  :key="field.key"
                  :xs="24"
                  :sm="field.span"
                >
                  <el-form-item :label="field.label">
                    <el-input
                      v-model="formData[field.key]"
                      :placeholder="'暂无'"
                      clearable
                    />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>
          </div>

          <!-- DeepSeek 分析卡片 -->
          <div v-if="result.return_reason || result.return_category" class="analysis-card">
            <div class="card-header">
              <el-icon color="var(--color-primary)"><MagicStick /></el-icon>
              <span>DeepSeek 智能分析</span>
            </div>
            <div class="analysis-body">
              <div class="analysis-item">
                <span class="analysis-label">退货原因</span>
                <span class="analysis-value">{{ result.return_reason || '-' }}</span>
              </div>
              <div class="analysis-item">
                <span class="analysis-label">退货分类</span>
                <el-tag type="warning" effect="light" round size="large">
                  {{ result.return_category || '-' }}
                </el-tag>
              </div>
            </div>
          </div>

          <!-- 校验警告 -->
          <el-alert
            v-if="validationWarnings.length > 0"
            type="warning"
            :closable="false"
            show-icon
            class="validation-alert"
          >
            <template #title>
              数据校验：{{ result.validation?.passed ? '通过（有警告）' : '不通过' }}
            </template>
            <div class="warning-list">
              <div v-for="(w, i) in validationWarnings" :key="i" class="warning-item">
                - {{ w }}
              </div>
            </div>
          </el-alert>

          <!-- 差异字段提示 -->
          <el-alert
            v-if="result.diff_fields && result.diff_fields.length > 0"
            type="warning"
            :closable="false"
            show-icon
            class="validation-alert"
          >
            <template #title>双引擎识别差异字段</template>
            <div class="warning-list">
              <div class="warning-item">
                - {{ result.diff_fields.map(fieldLabel).join('、') }}
              </div>
            </div>
          </el-alert>

          <!-- 操作按钮 -->
          <div class="action-bar">
            <el-button
              type="primary"
              size="large"
              round
              :loading="confirming"
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
        </el-col>
      </el-row>
    </section>
  </div>
</template>

<script setup>
import { ref, markRaw, computed, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import {
  UploadFilled, Loading, Picture, Document, MagicStick,
  View, Connection, Promotion, CircleCheck, Check, RefreshLeft,
} from '@element-plus/icons-vue';
import api from '../api';

const emit = defineEmits(['uploaded', 'navigate']);

const loading = ref(false);
const confirming = ref(false);
const result = ref(null);
const formData = ref({});
const resultSectionRef = ref(null);

// 特性卡片配置
const features = [
  {
    title: '双引擎OCR',
    desc: '智谱 + 阿里云并行识别，94% 准确率',
    icon: markRaw(View),
  },
  {
    title: '交叉验证',
    desc: '自动比对双引擎结果，置信度仲裁',
    icon: markRaw(Connection),
  },
  {
    title: 'DeepSeek分析',
    desc: '自动推断退货原因和智能分类',
    icon: markRaw(MagicStick),
  },
  {
    title: '飞书写入',
    desc: '确认后一键写入多维表格',
    icon: markRaw(Promotion),
  },
];

// 表单字段配置
const formFields = [
  { key: 'waybill_no', label: '运单号', span: 12 },
  { key: 'express_company', label: '快递公司', span: 12 },
  { key: 'rec_name', label: '收件人', span: 12 },
  { key: 'rec_phone', label: '收件电话', span: 12 },
  { key: 'rec_address', label: '收件地址', span: 24 },
  { key: 'sender_name', label: '寄件人', span: 12 },
  { key: 'sender_phone', label: '寄件电话', span: 12 },
  { key: 'sender_address', label: '寄件地址', span: 24 },
  { key: 'goods', label: '托寄物', span: 24 },
];

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

// 校验警告列表
const validationWarnings = computed(() => {
  return result.value?.validation?.warnings || [];
});

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

const fieldLabel = (f) => FIELD_LABELS[f] || f;

// 上传图片并调用API识别
const handleUpload = async (options) => {
  loading.value = true;
  try {
    const res = await api.upload(options.file);
    if (res.data.code === 0) {
      result.value = res.data.data;
      // 创建可编辑的表单数据副本
      formData.value = { ...(res.data.data.data || {}) };
      emit('uploaded', res.data.data);
      // 滚动到结果区域
      nextTick(() => {
        resultSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    } else {
      ElMessage.error(res.data.msg || '识别失败');
    }
  } catch (err) {
    ElMessage.error('上传失败：' + (err.response?.data?.msg || err.message));
  } finally {
    loading.value = false;
  }
};

// 确认写入飞书
const handleConfirm = async () => {
  confirming.value = true;
  try {
    const editedData = {
      waybill_no: formData.value.waybill_no || '',
      express_company: formData.value.express_company || '',
      rec_name: formData.value.rec_name || '',
      rec_phone: formData.value.rec_phone || '',
      rec_address: formData.value.rec_address || '',
      sender_name: formData.value.sender_name || '',
      sender_phone: formData.value.sender_phone || '',
      sender_address: formData.value.sender_address || '',
      goods: formData.value.goods || '',
    };
    const res = await api.confirm(result.value.record_id, editedData);
    if (res.data.code === 0) {
      ElMessage.success('已写入飞书多维表格');
      result.value = null;
      formData.value = {};
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
  result.value = null;
  formData.value = {};
};

// 跳转到记录列表
const goToRecords = () => {
  emit('navigate', 'records');
};
</script>

<style scoped>
.home-panel {
  width: 100%;
}

/* ===== Hero 区域 ===== */
.hero-section {
  text-align: center;
  padding: 40px 20px 36px;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-8);
  border-radius: 9999px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary-dark);
  letter-spacing: 1px;
  margin-bottom: 20px;
}

.hero-title {
  font-size: 40px;
  font-weight: 800;
  letter-spacing: 2px;
  margin-bottom: 12px;
  background: linear-gradient(135deg, var(--color-primary) 0%, var(--color-primary-light) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-subtitle {
  font-size: 15px;
  color: var(--color-text-secondary);
  letter-spacing: 0.5px;
}

/* ===== 特性卡片 ===== */
.features-section {
  margin-bottom: 36px;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.feature-card {
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
  padding: 24px 20px;
  display: flex;
  gap: 14px;
  align-items: flex-start;
  transition: all var(--transition);
  cursor: default;
}

.feature-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.feature-icon-wrapper {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--el-color-primary-light-9);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.feature-content {
  flex: 1;
  min-width: 0;
}

.feature-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 6px;
}

.feature-desc {
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 1.5;
}

/* ===== 上传区域 ===== */
.upload-section {
  padding: 8px 0;
}

.upload-zone {
  width: 100%;
}

.upload-zone :deep(.el-upload) {
  width: 100%;
}

.upload-zone :deep(.el-upload-dragger) {
  border: 2px dashed var(--color-border);
  border-radius: var(--radius-xl);
  background: #fff;
  padding: 56px 20px;
  transition: all var(--transition);
  height: auto;
  width: 100%;
}

.upload-zone :deep(.el-upload-dragger:hover) {
  border-color: var(--color-primary);
  background: var(--el-color-primary-light-9);
}

.upload-zone :deep(.el-upload-dragger.is-dragover) {
  border-color: var(--color-primary);
  background: var(--el-color-primary-light-8);
}

.upload-inner {
  text-align: center;
}

.upload-icon-circle {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  transition: all var(--transition);
}

.upload-zone :deep(.el-upload-dragger:hover) .upload-icon-circle {
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-light));
  color: #fff;
  transform: scale(1.05);
}

.upload-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 8px;
}

.upload-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin-bottom: 20px;
}

.upload-desc em {
  color: var(--color-primary);
  font-style: normal;
  font-weight: 600;
}

.upload-hints {
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
}

.hint-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  background: var(--color-bg-secondary);
  border-radius: 20px;
  font-size: 12px;
  color: var(--color-text-secondary);
}

.hint-chip .el-icon {
  font-size: 13px;
}

.upload-extra {
  text-align: center;
  margin-top: 16px;
}

/* ===== 加载中 ===== */
.loading-inner {
  text-align: center;
  padding: 20px 0;
}

.loading-spinner {
  position: relative;
  width: 64px;
  height: 64px;
  margin: 0 auto 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.spinner-ring {
  position: absolute;
  inset: 0;
  border: 3px solid var(--el-color-primary-light-8);
  border-top-color: var(--color-primary);
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

.spinner-icon {
  color: var(--color-primary);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 8px;
}

.loading-step {
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 24px;
}

.loading-progress {
  display: flex;
  gap: 8px;
  justify-content: center;
}

.loading-progress .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-primary);
  opacity: 0.3;
  animation: dotPulse 1.4s ease-in-out infinite;
}

.loading-progress .dot:nth-child(2) {
  animation-delay: 0.2s;
}

.loading-progress .dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes dotPulse {
  0%, 80%, 100% {
    opacity: 0.3;
    transform: scale(0.8);
  }
  40% {
    opacity: 1;
    transform: scale(1.2);
  }
}

/* ===== 结果展示区 ===== */
.result-section {
  background: #fff;
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.result-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: var(--el-color-primary-light-9);
  border-bottom: 1px solid var(--color-border);
  flex-wrap: wrap;
  gap: 10px;
}

.status-bar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.status-bar-right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.result-body {
  padding: 24px;
}

/* 图片预览卡片 */
.image-preview-card {
  background: var(--color-bg-secondary);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  overflow: hidden;
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-image {
  width: 100%;
  max-height: 400px;
  cursor: pointer;
}

.no-image {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: var(--color-text-muted);
  font-size: 14px;
}

/* 表单卡片 */
.form-card {
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  padding: 20px;
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  margin-bottom: 16px;
}

.card-header .el-icon {
  font-size: 17px;
}

.result-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.result-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 500;
  padding-bottom: 4px;
}

.result-form :deep(.el-input__wrapper) {
  border-radius: var(--radius-md);
}

/* DeepSeek 分析卡片 */
.analysis-card {
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-8);
  border-radius: var(--radius-lg);
  padding: 20px;
  margin-bottom: 16px;
}

.analysis-body {
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

.analysis-label {
  font-size: 12px;
  color: var(--color-text-muted);
  font-weight: 500;
}

.analysis-value {
  font-size: 15px;
  color: var(--color-text);
  font-weight: 600;
  line-height: 1.5;
}

/* 校验警告 */
.validation-alert {
  margin-bottom: 16px;
  border-radius: var(--radius-md);
}

.warning-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 4px;
}

.warning-item {
  font-size: 13px;
  line-height: 1.6;
}

/* 操作按钮 */
.action-bar {
  display: flex;
  gap: 12px;
  justify-content: center;
  padding: 8px 0 4px;
}

.action-bar .el-button {
  min-width: 150px;
}

/* ===== 响应式 ===== */
@media (max-width: 992px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .hero-title {
    font-size: 32px;
  }
}

@media (max-width: 768px) {
  .hero-section {
    padding: 24px 16px 28px;
  }

  .hero-title {
    font-size: 28px;
  }

  .hero-subtitle {
    font-size: 14px;
  }

  .features-grid {
    grid-template-columns: 1fr;
  }

  .feature-card {
    padding: 18px 16px;
  }

  .upload-zone :deep(.el-upload-dragger) {
    padding: 40px 16px;
  }

  .result-status-bar {
    padding: 14px 16px;
  }

  .result-body {
    padding: 16px;
  }

  .analysis-body {
    flex-direction: column;
    gap: 12px;
  }

  .action-bar {
    flex-direction: column;
  }

  .action-bar .el-button {
    width: 100%;
  }
}
</style>
