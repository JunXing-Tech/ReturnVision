<template>
  <div class="upload-panel">
    <!-- ===== 空状态：上传区域 ===== -->
    <div v-if="!loading && !result" class="upload-zone-wrapper">
      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="true"
        :show-file-list="false"
        :http-request="handleUpload"
        accept="image/*"
        class="upload-zone"
      >
        <div class="upload-inner">
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
      </el-upload>
    </div>

    <!-- ===== 加载中 ===== -->
    <div v-if="loading" class="loading-wrapper">
      <div class="loading-card">
        <div class="loading-spinner">
          <span class="spinner-ring"></span>
          <el-icon class="spinner-icon" :size="28"><Loading /></el-icon>
        </div>
        <h3 class="loading-title">正在智能识别中</h3>
        <p class="loading-step">双引擎 OCR 并行识别 → 交叉验证 → DeepSeek 分析</p>
        <div class="loading-progress">
          <span class="dot"></span>
          <span class="dot"></span>
          <span class="dot"></span>
        </div>
      </div>
    </div>

    <!-- ===== 识别结果 ===== -->
    <div v-if="!loading && result" class="result-wrapper">
      <!-- 顶部状态条 -->
      <div class="status-bar">
        <div class="status-left">
          <el-icon class="status-icon" :color="validationColor(result.cross_validation)"><CircleCheck /></el-icon>
          <span class="status-text">识别完成</span>
          <el-tag
            :type="validationTagType(result.cross_validation)"
            effect="light"
            round
            size="large"
          >
            {{ validationLabel(result.cross_validation) }}
          </el-tag>
          <el-tag
            :type="confidenceTagType(result.confidence)"
            effect="plain"
            round
            size="large"
          >
            置信度：{{ confidenceLabel(result.confidence) }}
          </el-tag>
        </div>
        <div class="status-right">
          <span class="record-id">记录 #{{ result.record_id }}</span>
        </div>
      </div>

      <el-row :gutter="20">
        <!-- 左侧：图片预览 -->
        <el-col :xs="24" :md="9">
          <div class="preview-card">
            <div class="card-label">
              <el-icon><Picture /></el-icon>
              <span>面单图片</span>
            </div>
            <div class="preview-image-box">
              <el-image
                :src="result.image_url"
                fit="contain"
                :preview-src-list="[result.image_url]"
                preview-teleported
                class="preview-image"
              />
            </div>
          </div>
        </el-col>

        <!-- 右侧：可编辑表单 -->
        <el-col :xs="24" :md="15">
          <div class="form-card">
            <div class="card-label">
              <el-icon><EditPen /></el-icon>
              <span>识别数据（可编辑）</span>
            </div>
            <el-form :model="editData" label-position="top" class="edit-form">
              <div class="form-section-title">基本信息</div>
              <el-row :gutter="14">
                <el-col :span="12">
                  <el-form-item label="运单号">
                    <el-input v-model="editData.waybill_no" :class="{ 'field-diff': isDiff('waybill_no') }" clearable />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="快递公司">
                    <el-input v-model="editData.express_company" :class="{ 'field-diff': isDiff('express_company') }" clearable />
                  </el-form-item>
                </el-col>
              </el-row>

              <div class="form-section-title">收件信息</div>
              <el-row :gutter="14">
                <el-col :span="12">
                  <el-form-item label="收件人">
                    <el-input v-model="editData.rec_name" :class="{ 'field-diff': isDiff('rec_name') }" clearable />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="收件电话">
                    <el-input v-model="editData.rec_phone" :class="{ 'field-diff': isDiff('rec_phone') }" clearable />
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="收件地址">
                    <el-input v-model="editData.rec_address" :class="{ 'field-diff': isDiff('rec_address') }" clearable />
                  </el-form-item>
                </el-col>
              </el-row>

              <div class="form-section-title">寄件信息</div>
              <el-row :gutter="14">
                <el-col :span="12">
                  <el-form-item label="寄件人">
                    <el-input v-model="editData.sender_name" :class="{ 'field-diff': isDiff('sender_name') }" clearable />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="寄件电话">
                    <el-input v-model="editData.sender_phone" :class="{ 'field-diff': isDiff('sender_phone') }" clearable />
                  </el-form-item>
                </el-col>
                <el-col :span="24">
                  <el-form-item label="寄件地址">
                    <el-input v-model="editData.sender_address" :class="{ 'field-diff': isDiff('sender_address') }" clearable />
                  </el-form-item>
                </el-col>
              </el-row>

              <div class="form-section-title">货物信息</div>
              <el-row :gutter="14">
                <el-col :span="12">
                  <el-form-item label="托寄物">
                    <el-input v-model="editData.goods" :class="{ 'field-diff': isDiff('goods') }" clearable />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-form>
          </div>
        </el-col>
      </el-row>

      <!-- DeepSeek 分析结果 -->
      <div v-if="result.return_reason || result.return_category" class="analysis-card">
        <div class="card-label">
          <el-icon color="#0d9488"><MagicStick /></el-icon>
          <span>DeepSeek 智能分析</span>
        </div>
        <div class="analysis-grid">
          <div class="analysis-item">
            <span class="analysis-key">退货原因</span>
            <span class="analysis-val">{{ result.return_reason || '—' }}</span>
          </div>
          <div class="analysis-item">
            <span class="analysis-key">退货分类</span>
            <el-tag type="warning" effect="light" round size="large">{{ result.return_category || '—' }}</el-tag>
          </div>
        </div>
      </div>

      <!-- 差异字段提示 -->
      <div v-if="result.diff_fields && result.diff_fields.length > 0" class="diff-card">
        <div class="diff-header">
          <el-icon color="#f59e0b"><WarningFilled /></el-icon>
          <span>以下字段双引擎识别存在差异，请重点核对</span>
        </div>
        <div class="diff-tags">
          <el-tag
            v-for="f in result.diff_fields"
            :key="f"
            type="warning"
            effect="plain"
            size="small"
          >
            {{ fieldLabel(f) }}
          </el-tag>
        </div>
      </div>

      <!-- 数据校验警告 -->
      <div v-if="result.validation && result.validation.warnings && result.validation.warnings.length > 0" class="validation-card">
        <div class="card-label">
          <el-icon :color="result.validation.passed ? '#10b981' : '#ef4444'"><CircleCheck /></el-icon>
          <span>数据校验</span>
          <el-tag :type="result.validation.passed ? 'success' : 'danger'" effect="light" size="small" round>
            {{ result.validation.passed ? '通过' : '不通过' }}
          </el-tag>
        </div>
        <div class="warning-list">
          <div v-for="(w, i) in result.validation.warnings" :key="i" class="warning-item">
            <el-icon color="#f59e0b"><WarningFilled /></el-icon>
            <span>{{ w }}</span>
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
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import {
  UploadFilled, Loading, Picture, Document, MagicStick,
  EditPen, CircleCheck, WarningFilled, Check, RefreshLeft,
} from '@element-plus/icons-vue';
import api from '../api';

const loading = ref(false);
const confirming = ref(false);
const result = ref(null);
const editData = reactive({});

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

// 上传图片
const handleUpload = async (options) => {
  loading.value = true;
  result.value = null;
  try {
    const res = await api.upload(options.file);
    if (res.data.code === 0) {
      result.value = res.data.data;
      Object.assign(editData, result.value.data);
      ElMessage.success('识别完成');
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
    const res = await api.confirm(result.value.record_id, editData);
    if (res.data.code === 0) {
      ElMessage.success('已写入飞书多维表格');
      handleReset();
    } else {
      ElMessage.error(res.data.msg || '写入失败');
    }
  } catch (err) {
    ElMessage.error('确认失败：' + (err.response?.data?.msg || err.message));
  } finally {
    confirming.value = false;
  }
};

// 重置
const handleReset = () => {
  result.value = null;
  Object.keys(editData).forEach((k) => delete editData[k]);
};

// 判断字段是否有差异
const isDiff = (field) => {
  return result.value?.diff_fields?.includes(field);
};

const fieldLabel = (f) => FIELD_LABELS[f] || f;

// 交叉验证状态映射
const validationTagType = (val) => {
  const map = { accept: 'success', review: 'warning', manual: 'danger' };
  return map[val] || 'info';
};

const validationLabel = (val) => {
  const map = { accept: '自动采用', review: '需复核', manual: '转人工' };
  return map[val] || val;
};

const validationColor = (val) => {
  const map = { accept: '#10b981', review: '#f59e0b', manual: '#ef4444' };
  return map[val] || '#909399';
};

// 置信度映射
const confidenceTagType = (c) => {
  const map = { high: 'success', medium: 'warning', low: 'danger' };
  return map[c] || 'info';
};

const confidenceLabel = (c) => {
  const map = { high: '高', medium: '中', low: '低' };
  return map[c] || c;
};
</script>

<style scoped>
.upload-panel { width: 100%; }

/* ===== 上传区域 ===== */
.upload-zone-wrapper { padding: 20px 0; }

.upload-zone {
  width: 100%;
}
.upload-zone :deep(.el-upload-dragger) {
  border: 2px dashed #cbd5e1;
  border-radius: var(--rv-card-radius);
  background: #fff;
  padding: 56px 20px;
  transition: all 0.3s ease;
  height: auto;
}
.upload-zone :deep(.el-upload-dragger:hover) {
  border-color: #0d9488;
  background: #f7fdfb;
}
.upload-zone :deep(.el-upload-dragger.is-dragover) {
  border-color: #0d9488;
  background: #ecfbf8;
}

.upload-inner { text-align: center; }

.upload-icon-circle {
  width: 80px;
  height: 80px;
  margin: 0 auto 20px;
  border-radius: 50%;
  background: linear-gradient(135deg, #e6fbf6, #ccf5ed);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #0d9488;
  transition: all 0.3s ease;
}
.upload-zone :deep(.el-upload-dragger:hover) .upload-icon-circle {
  background: linear-gradient(135deg, #0d9488, #14b8a6);
  color: #fff;
  transform: scale(1.05);
}

.upload-title {
  font-size: 20px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}
.upload-desc {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 20px;
}
.upload-desc em {
  color: #0d9488;
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
  background: #f1f5f9;
  border-radius: 20px;
  font-size: 12px;
  color: #64748b;
}
.hint-chip .el-icon { font-size: 13px; }

/* ===== 加载中 ===== */
.loading-wrapper { padding: 40px 0; }
.loading-card {
  background: #fff;
  border-radius: var(--rv-card-radius);
  box-shadow: var(--rv-shadow);
  padding: 64px 20px;
  text-align: center;
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
  border: 3px solid #e6fbf6;
  border-top-color: #0d9488;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}
.spinner-icon { color: #0d9488; }
@keyframes spin { to { transform: rotate(360deg); } }

.loading-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}
.loading-step {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 24px;
}
.loading-progress { display: flex; gap: 8px; justify-content: center; }
.loading-progress .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #0d9488;
  opacity: 0.3;
  animation: dotPulse 1.4s ease-in-out infinite;
}
.loading-progress .dot:nth-child(2) { animation-delay: 0.2s; }
.loading-progress .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes dotPulse {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1.2); }
}

/* ===== 结果区域 ===== */
.result-wrapper { animation: fadeIn 0.3s ease; }
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 顶部状态条 */
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: var(--rv-card-radius);
  box-shadow: var(--rv-shadow);
  padding: 14px 20px;
  margin-bottom: 16px;
}
.status-left { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.status-icon { font-size: 20px; }
.status-text { font-size: 15px; font-weight: 600; color: #1f2937; margin-right: 4px; }
.record-id { font-size: 13px; color: #94a3b8; font-weight: 500; }

/* 卡片通用样式 */
.preview-card,
.form-card,
.analysis-card,
.diff-card,
.validation-card {
  background: #fff;
  border-radius: var(--rv-card-radius);
  box-shadow: var(--rv-shadow);
  padding: 18px 20px;
  margin-bottom: 16px;
  transition: box-shadow 0.25s ease;
}
.preview-card:hover,
.form-card:hover,
.analysis-card:hover,
.validation-card:hover {
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

/* 图片预览 */
.preview-image-box {
  border-radius: 10px;
  overflow: hidden;
  background: #f8fafc;
  border: 1px solid #e8efee;
}
.preview-image {
  width: 100%;
  max-height: 420px;
  display: block;
}

/* 表单 */
.form-section-title {
  font-size: 12px;
  font-weight: 600;
  color: #0d9488;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 4px 0;
  margin: 6px 0 2px;
  border-bottom: 1px solid #f0f5f4;
}
.edit-form :deep(.el-form-item) { margin-bottom: 14px; }
.edit-form :deep(.el-form-item__label) {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
  padding-bottom: 2px;
}
.edit-form :deep(.el-input__wrapper) {
  border-radius: 8px;
  transition: box-shadow 0.2s ease;
}

/* 差异字段高亮 */
.edit-form :deep(.field-diff .el-input__wrapper) {
  box-shadow: 0 0 0 1px #f59e0b inset;
  background: #fffbeb;
}

/* DeepSeek 分析 */
.analysis-grid {
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
.diff-card {
  background: #fffbeb;
  border: 1px solid #fde68a;
  box-shadow: none;
}
.diff-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #92400e;
  margin-bottom: 10px;
}
.diff-tags { display: flex; gap: 6px; flex-wrap: wrap; }

/* 校验警告 */
.warning-list { display: flex; flex-direction: column; gap: 8px; }
.warning-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #78716c;
  background: #fffbeb;
  border-radius: 8px;
  padding: 8px 12px;
}
.warning-item .el-icon { font-size: 15px; flex-shrink: 0; }

/* 操作按钮 */
.action-bar {
  display: flex;
  gap: 12px;
  justify-content: center;
  padding: 8px 0 4px;
}
.action-bar .el-button { min-width: 150px; }
</style>
