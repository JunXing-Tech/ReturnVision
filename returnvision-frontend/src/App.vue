<template>
  <div class="app-container">
    <!-- 顶部导航栏 -->
    <header class="app-header">
      <div class="header-inner">
        <div class="brand">
          <div class="brand-logo">
            <el-icon :size="22"><Box /></el-icon>
          </div>
          <div class="brand-text">
            <h1>退运智录</h1>
            <span class="brand-sub">快递面单 OCR 智能识别系统</span>
          </div>
        </div>
        <nav class="nav-tabs">
          <button
            v-for="tab in tabs"
            :key="tab.name"
            :class="['nav-tab', { active: activeTab === tab.name }]"
            @click="activeTab = tab.name"
          >
            <el-icon class="tab-icon"><component :is="tab.icon" /></el-icon>
            <span>{{ tab.label }}</span>
          </button>
        </nav>
      </div>
    </header>

    <!-- 主内容区 -->
    <main class="app-main">
      <div class="main-inner">
        <HomePanel
          v-if="activeTab === 'home'"
          @uploaded="handleUploaded"
        />
        <ProcessPanel
          v-if="activeTab === 'process'"
          :result="uploadResult"
          @confirmed="handleConfirmed"
          @reset="handleReset"
        />
        <RecordsPanel v-if="activeTab === 'records'" />
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, markRaw } from 'vue';
import { ElMessage } from 'element-plus';
import { Box, House, Timer, Document } from '@element-plus/icons-vue';
import HomePanel from './components/HomePanel.vue';
import ProcessPanel from './components/ProcessPanel.vue';
import RecordsPanel from './components/RecordsPanel.vue';

const activeTab = ref('home');
const uploadResult = ref(null);
const tabs = [
  { name: 'home', label: '主页', icon: markRaw(House) },
  { name: 'process', label: '流程展示', icon: markRaw(Timer) },
  { name: 'records', label: '记录列表', icon: markRaw(Document) },
];

// HomePanel 上传成功后，保存结果并切换到流程展示页
const handleUploaded = (result) => {
  uploadResult.value = result;
  activeTab.value = 'process';
};

// ProcessPanel 确认写入飞书成功后，提示并跳转到记录列表
const handleConfirmed = () => {
  uploadResult.value = null;
  activeTab.value = 'records';
  ElMessage.success('已写入飞书多维表格');
};

// ProcessPanel 重新上传，清空结果并返回主页
const handleReset = () => {
  uploadResult.value = null;
  activeTab.value = 'home';
};
</script>

<style>
/* ===== 全局主题：teal 青色系 ===== */
:root {
  --el-color-primary: #0d9488;
  --el-color-primary-light-3: #3ba89e;
  --el-color-primary-light-5: #6ac5bb;
  --el-color-primary-light-7: #9ad7d0;
  --el-color-primary-light-8: #b3e2dc;
  --el-color-primary-light-9: #ccebe7;
  --el-color-primary-dark-2: #0a766d;
  --rv-bg: #f0f5f4;
  --rv-card-radius: 14px;
  --rv-shadow: 0 1px 3px rgba(13, 148, 136, 0.06), 0 1px 2px rgba(0, 0, 0, 0.04);
  --rv-shadow-hover: 0 8px 24px rgba(13, 148, 136, 0.12), 0 2px 8px rgba(0, 0, 0, 0.06);
}

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
  font-family: system-ui, "Microsoft YaHei", -apple-system, sans-serif;
  background: var(--rv-bg);
  color: #1f2937;
  -webkit-font-smoothing: antialiased;
}

/* ===== 布局 ===== */
.app-container { min-height: 100vh; display: flex; flex-direction: column; }

.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  border-bottom: 1px solid #e8efee;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.header-inner {
  max-width: 1400px;
  margin: 0 auto;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
}

.brand { display: flex; align-items: center; gap: 12px; }

.brand-logo {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #0d9488, #14b8a6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-text h1 {
  font-size: 19px;
  font-weight: 700;
  color: #0d9488;
  letter-spacing: 0.5px;
  line-height: 1.2;
}

.brand-sub {
  font-size: 12px;
  color: #94a3b8;
  letter-spacing: 0.3px;
}

/* ===== 导航标签 ===== */
.nav-tabs { display: flex; gap: 4px; }

.nav-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border: none;
  background: transparent;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  color: #64748b;
  cursor: pointer;
  transition: all 0.25s ease;
}

.nav-tab:hover {
  background: var(--el-color-primary-light-9);
  color: #0d9488;
}

.nav-tab.active {
  background: #0d9488;
  color: #fff;
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.3);
}

.tab-icon { font-size: 16px; }

/* ===== 主内容 ===== */
.app-main {
  flex: 1;
  padding: 28px 32px 48px;
}

.main-inner {
  max-width: 1400px;
  margin: 0 auto;
}
</style>
