<template>
  <div class="home-panel">
    <!-- ===== 上半部分：项目介绍 ===== -->
    <section class="intro-section">
      <div class="intro-hero">
        <div class="hero-badge">ReturnVision</div>
        <h1 class="hero-title">退运智录</h1>
        <p class="hero-subtitle">
          拍照识别快递面单 → 双引擎交叉验证 → DeepSeek分析 → 飞书写入
        </p>
      </div>

      <!-- 特性卡片 -->
      <div class="features-grid">
        <div
          v-for="feature in features"
          :key="feature.title"
          class="feature-card"
        >
          <div class="feature-icon" :style="{ background: feature.bg }">
            <el-icon :size="24" :color="feature.color"><component :is="feature.icon" /></el-icon>
          </div>
          <h3 class="feature-title">{{ feature.title }}</h3>
          <p class="feature-desc">{{ feature.desc }}</p>
        </div>
      </div>
    </section>

    <!-- ===== 下半部分：上传区域 ===== -->
    <section class="upload-section">
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
          <p class="loading-step">双引擎 OCR 并行识别 → 交叉验证 → DeepSeek 分析</p>
          <div class="loading-progress">
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
          </div>
        </div>
      </el-upload>
    </section>
  </div>
</template>

<script setup>
import { ref, markRaw } from 'vue';
import { ElMessage } from 'element-plus';
import {
  UploadFilled, Loading, Picture, Document, MagicStick,
  View, Connection, Promotion,
} from '@element-plus/icons-vue';
import api from '../api';

const loading = ref(false);

// 特性卡片配置
const features = [
  {
    title: '双引擎OCR',
    desc: '智谱 + 阿里云并行识别，94% 准确率',
    icon: markRaw(View),
    color: '#0d9488',
    bg: 'linear-gradient(135deg, #e6fbf6, #ccf5ed)',
  },
  {
    title: '智能交叉验证',
    desc: '自动比对双引擎结果，置信度仲裁',
    icon: markRaw(Connection),
    color: '#0891b2',
    bg: 'linear-gradient(135deg, #e0f7ff, #ccf0ff)',
  },
  {
    title: 'DeepSeek分析',
    desc: '自动推断退货原因和智能分类',
    icon: markRaw(MagicStick),
    color: '#7c3aed',
    bg: 'linear-gradient(135deg, #f3e8ff, #e9d5ff)',
  },
  {
    title: '飞书自动写入',
    desc: '确认后一键写入多维表格',
    icon: markRaw(Promotion),
    color: '#ea580c',
    bg: 'linear-gradient(135deg, #fff3e6, #ffe6cc)',
  },
];

// 上传图片并调用API识别
const handleUpload = async (options) => {
  loading.value = true;
  try {
    const res = await api.upload(options.file);
    if (res.data.code === 0) {
      // 上传成功后通知App.vue切换到流程展示页
      emit('uploaded', res.data.data);
    } else {
      ElMessage.error(res.data.msg || '识别失败');
    }
  } catch (err) {
    ElMessage.error('上传失败：' + (err.response?.data?.msg || err.message));
  } finally {
    loading.value = false;
  }
};

// emit 定义
const emit = defineEmits(['uploaded']);
</script>

<style scoped>
.home-panel { width: 100%; }

/* ===== 项目介绍 ===== */
.intro-section {
  text-align: center;
  margin-bottom: 36px;
}

.intro-hero {
  padding: 20px 0 28px;
}

.hero-badge {
  display: inline-block;
  padding: 4px 14px;
  background: var(--el-color-primary-light-9);
  color: #0d9488;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1px;
  margin-bottom: 14px;
}

.hero-title {
  font-size: 34px;
  font-weight: 800;
  color: #0d9488;
  letter-spacing: 2px;
  margin-bottom: 10px;
}

.hero-subtitle {
  font-size: 15px;
  color: #64748b;
  letter-spacing: 0.5px;
}

/* ===== 特性卡片 ===== */
.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-top: 28px;
}

.feature-card {
  background: #fff;
  border-radius: var(--rv-card-radius);
  box-shadow: var(--rv-shadow);
  padding: 24px 20px;
  text-align: left;
  transition: all 0.3s ease;
  cursor: default;
}

.feature-card:hover {
  box-shadow: var(--rv-shadow-hover);
  transform: translateY(-4px);
}

.feature-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 14px;
}

.feature-title {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 6px;
}

.feature-desc {
  font-size: 13px;
  color: #94a3b8;
  line-height: 1.5;
}

/* ===== 上传区域 ===== */
.upload-section { padding: 8px 0; }

.upload-zone { width: 100%; }
.upload-zone :deep(.el-upload) { width: 100%; }
.upload-zone :deep(.el-upload-dragger) {
  border: 2px dashed #cbd5e1;
  border-radius: var(--rv-card-radius);
  background: #fff;
  padding: 56px 20px;
  transition: all 0.3s ease;
  height: auto;
  width: 100%;
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
.loading-inner { text-align: center; padding: 20px 0; }

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

/* ===== 响应式 ===== */
@media (max-width: 900px) {
  .features-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .hero-title { font-size: 28px; }
}

@media (max-width: 500px) {
  .features-grid {
    grid-template-columns: 1fr;
  }
}
</style>
