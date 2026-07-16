<template>
  <div class="home-panel">
    <!-- 上传区域在最顶部 -->
    <div class="upload-section">
      <el-upload
        drag
        :auto-upload="true"
        :show-file-list="false"
        :http-request="handleUpload"
        accept="image/*"
        :disabled="loading"
      >
        <div v-if="!loading" class="upload-content">
          <el-icon :size="48" color="#0d9488"><UploadFilled /></el-icon>
          <p class="upload-title">点击或拖拽上传快递面单图片</p>
          <p class="upload-hint">支持 JPG / PNG，最大 10MB</p>
        </div>
        <div v-else class="loading-content">
          <el-icon class="is-loading" :size="36" color="#0d9488"><Loading /></el-icon>
          <p class="loading-text">正在智能识别中...</p>
          <p class="loading-hint">双引擎OCR + DeepSeek分析</p>
        </div>
      </el-upload>
    </div>

    <!-- 项目介绍 -->
    <div class="intro-section">
      <div class="intro-header">
        <span class="intro-badge">ReturnVision</span>
        <h1 class="intro-title">退运智录</h1>
        <p class="intro-subtitle">拍照识别快递面单 → 双引擎交叉验证 → DeepSeek分析 → 飞书写入</p>
      </div>

      <div class="features-grid">
        <div v-for="f in features" :key="f.title" class="feature-card">
          <div class="feature-icon" :style="{ background: f.bg }">{{ f.icon }}</div>
          <h3>{{ f.title }}</h3>
          <p>{{ f.desc }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import { UploadFilled, Loading } from '@element-plus/icons-vue';
import api from '../api';

const emit = defineEmits(['uploaded']);
const loading = ref(false);

const features = [
  { icon: '🔍', title: '双引擎OCR', desc: '智谱GLM-OCR + 阿里云面单OCR并行识别，准确率94%+', bg: '#e0f2fe' },
  { icon: '✅', title: '交叉验证', desc: '自动比对双引擎结果，置信度仲裁冲突字段', bg: '#f0fdf4' },
  { icon: '🤖', title: 'DeepSeek分析', desc: 'AI推断退货原因，智能分类质量问题/物流问题等', bg: '#fef3c7' },
  { icon: '📝', title: '飞书写入', desc: '确认后一键写入飞书多维表格，无需手动录入', bg: '#fce7f3' },
];

const handleUpload = async (options) => {
  loading.value = true;
  try {
    const res = await api.upload(options.file);
    if (res.data.code === 0) {
      ElMessage.success('识别完成');
      emit('uploaded', res.data.data);
    } else {
      ElMessage.error(res.data.msg);
    }
  } catch (err) {
    ElMessage.error('上传失败：' + (err.response?.data?.msg || err.message));
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
.home-panel { display: flex; flex-direction: column; gap: 32px; }

/* Upload */
.upload-section :deep(.el-upload-dragger) {
  width: 100%; border: 2px dashed var(--color-border); border-radius: var(--radius-xl);
  padding: 48px 24px; transition: var(--transition); background: #fff;
}
.upload-section :deep(.el-upload-dragger:hover) { border-color: var(--color-primary); }
.upload-content { text-align: center; }
.upload-title { font-size: 16px; font-weight: 600; color: var(--color-text); margin: 12px 0 4px; }
.upload-hint { font-size: 13px; color: var(--color-text-muted); }
.loading-content { text-align: center; }
.loading-text { font-size: 15px; font-weight: 500; color: var(--color-primary); margin: 12px 0 4px; }
.loading-hint { font-size: 12px; color: var(--color-text-muted); }

/* Intro */
.intro-header { text-align: center; margin-bottom: 28px; }
.intro-badge {
  display: inline-block; padding: 4px 12px; border-radius: 999px;
  background: var(--color-bg-secondary); color: var(--color-primary);
  font-size: 12px; font-weight: 600; letter-spacing: 0.5px; margin-bottom: 12px;
}
.intro-title { font-size: 32px; font-weight: 800; color: var(--color-text); margin-bottom: 8px; }
.intro-subtitle { font-size: 15px; color: var(--color-text-secondary); }

.features-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px;
}
.feature-card {
  background: #fff; border-radius: var(--radius-lg); padding: 24px 20px;
  box-shadow: var(--shadow-sm); transition: var(--transition); border: 1px solid var(--color-border);
}
.feature-card:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); }
.feature-icon {
  width: 44px; height: 44px; border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: center; font-size: 22px; margin-bottom: 12px;
}
.feature-card h3 { font-size: 15px; font-weight: 600; color: var(--color-text); margin-bottom: 6px; }
.feature-card p { font-size: 13px; color: var(--color-text-secondary); line-height: 1.5; }

@media (max-width: 992px) { .features-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 768px) {
  .features-grid { grid-template-columns: 1fr; }
  .intro-title { font-size: 24px; }
  .upload-section :deep(.el-upload-dragger) { padding: 32px 16px; }
}
</style>
