<template>
  <div class="app-container">
    <!-- 顶部导航栏 -->
    <header class="app-header">
      <div class="header-inner">
        <div class="brand">
          <div class="brand-logo">
            <el-icon :size="20"><Box /></el-icon>
          </div>
          <h1 class="brand-title">退运智录</h1>
        </div>
        <!-- PC端导航 -->
        <nav v-if="!isMobile" class="nav-tabs">
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
          @navigate="handleNavigate"
        />
        <RecordsPanel v-if="activeTab === 'records'" />
      </div>
    </main>

    <!-- 底部版权 -->
    <footer class="app-footer">
      <p class="copyright">退运智录 ReturnVision · 快递面单 OCR 智能识别系统</p>
    </footer>

    <!-- 移动端底部Tab栏 -->
    <nav v-if="isMobile" class="mobile-tab-bar">
      <button
        v-for="tab in tabs"
        :key="tab.name"
        :class="['mobile-tab', { active: activeTab === tab.name }]"
        @click="activeTab = tab.name"
      >
        <el-icon class="tab-icon"><component :is="tab.icon" /></el-icon>
        <span>{{ tab.label }}</span>
      </button>
    </nav>
  </div>
</template>

<script setup>
import { ref, markRaw } from 'vue';
import { Box, House, Document } from '@element-plus/icons-vue';
import { useMobile } from './composables/useMobile';
import HomePanel from './components/HomePanel.vue';
import RecordsPanel from './components/RecordsPanel.vue';

const { isMobile } = useMobile();
const activeTab = ref('home');
const uploadResult = ref(null);

const tabs = [
  { name: 'home', label: '主页', icon: markRaw(House) },
  { name: 'records', label: '记录列表', icon: markRaw(Document) },
];

// HomePanel 上传成功后保存结果，保持当前tab不变（结果在主页内联展示）
const handleUploaded = (result) => {
  uploadResult.value = result;
};

// HomePanel 请求切换页面（如查看记录列表）
const handleNavigate = (tab) => {
  activeTab.value = tab;
};
</script>

<style>
/* ===== 全局 CSS 变量 - teal 青色系 ===== */
:root {
  /* 品牌色 */
  --color-primary: #0d9488;
  --color-primary-light: #14b8a6;
  --color-primary-dark: #0f766e;

  /* 背景色 */
  --color-bg: #f8fafc;
  --color-bg-secondary: #f1f5f9;

  /* 文字色 */
  --color-text: #1e293b;
  --color-text-secondary: #64748b;
  --color-text-muted: #94a3b8;

  /* 边框 */
  --color-border: #e2e8f0;

  /* 功能色 */
  --color-success: #22c55e;
  --color-warning: #eab308;
  --color-error: #ef4444;

  /* 阴影 */
  --shadow-sm: 0 1px 3px rgba(0, 0, 0, 0.05);
  --shadow-md: 0 4px 12px -2px rgba(0, 0, 0, 0.08);
  --shadow-lg: 0 12px 24px -4px rgba(0, 0, 0, 0.12);

  /* 圆角 */
  --radius-sm: 6px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 16px;

  /* 过渡 */
  --transition: 200ms ease-out;

  /* Element Plus 主色覆盖 */
  --el-color-primary: #0d9488;
  --el-color-primary-light-3: #2dd4bf;
  --el-color-primary-light-5: #5eead4;
  --el-color-primary-light-7: #99f6e4;
  --el-color-primary-light-8: #ccfbf1;
  --el-color-primary-light-9: #f0fdfa;
  --el-color-primary-dark-2: #0f766e;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: system-ui, "Microsoft YaHei", -apple-system, sans-serif;
  background: linear-gradient(180deg, var(--color-bg) 0%, var(--color-bg-secondary) 100%);
  background-attachment: fixed;
  color: var(--color-text);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* ===== 布局 ===== */
.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ===== Header ===== */
.app-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-bottom: 1px solid var(--color-border);
  box-shadow: var(--shadow-sm);
}

.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-logo {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-primary), var(--color-primary-light));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-title {
  font-size: 19px;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: 1px;
  line-height: 1;
}

/* ===== PC 导航标签 ===== */
.nav-tabs {
  display: flex;
  gap: 4px;
}

.nav-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border: none;
  background: transparent;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-family: inherit;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: all var(--transition);
}

.nav-tab:hover {
  background: var(--el-color-primary-light-9);
  color: var(--color-primary);
}

.nav-tab.active {
  background: var(--color-primary);
  color: #fff;
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.3);
}

.tab-icon {
  font-size: 16px;
}

/* ===== 主内容 ===== */
.app-main {
  flex: 1;
  padding: 28px 24px 48px;
}

.main-inner {
  max-width: 1200px;
  margin: 0 auto;
}

/* ===== Footer ===== */
.app-footer {
  text-align: center;
  padding: 32px 24px;
  position: relative;
}

.app-footer::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100px;
  height: 1px;
  background: var(--color-border);
}

.copyright {
  color: var(--color-text-muted);
  font-size: 13px;
  letter-spacing: 0.5px;
}

/* ===== 移动端底部Tab栏 ===== */
.mobile-tab-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  border-top: 1px solid var(--color-border);
  display: flex;
  z-index: 100;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.06);
}

.mobile-tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: none;
  background: transparent;
  font-size: 12px;
  font-family: inherit;
  color: var(--color-text-muted);
  cursor: pointer;
  transition: color var(--transition);
}

.mobile-tab.active {
  color: var(--color-primary);
  font-weight: 600;
}

.mobile-tab .tab-icon {
  font-size: 22px;
}

/* ===== 移动端响应式 ===== */
@media (max-width: 768px) {
  .header-inner {
    padding: 0 16px;
  }

  .brand-title {
    font-size: 17px;
  }

  .app-main {
    padding: 20px 16px 80px;
  }

  .app-footer {
    padding-bottom: 76px;
  }
}
</style>
