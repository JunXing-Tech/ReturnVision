<template>
  <!-- 步骤1：app-shell 网格布局（侧边栏 + 主内容区） -->
  <div class="app-shell">
    <!-- 步骤2：侧边栏 -->
    <aside class="sidebar">
      <!-- 工作区切换 -->
      <div class="workspace">
        <div class="ws-avatar">RV</div>
        <div class="ws-info">
          <div class="ws-name">退运智录</div>
          <div class="ws-role">退运管理</div>
        </div>
      </div>

      <!-- 导航列表 -->
      <nav class="nav-list">
        <button v-for="tab in tabs" :key="tab.key"
          :class="['nav-item', { active: activeTab === tab.key }]"
          @click="activeTab = tab.key">
          <component :is="tab.icon" />
          <span>{{ tab.label }}</span>
        </button>
      </nav>

      <!-- 分隔线 -->
      <div class="nav-divider"></div>

      <!-- 底部用户信息 -->
      <div class="user-pill">
        <div class="user-avatar">RV</div>
        <div class="user-info">
          <div class="user-name">运营员</div>
          <div class="user-role">退运管理</div>
        </div>
      </div>
    </aside>

    <!-- 步骤3：主内容区 -->
    <main class="content">
      <!-- 顶栏 -->
      <header class="topbar">
        <div class="search-box">
          <el-icon class="search-icon"><Search /></el-icon>
          <input class="search-input" placeholder="搜索运单号、收件人..." />
        </div>
        <div class="toolbar">
          <button class="icon-btn">
            <el-icon><Bell /></el-icon>
          </button>
          <div class="user-chip">
            <div class="chip-avatar">RV</div>
            <div class="chip-info">
              <div class="chip-name">运营员</div>
              <div class="chip-role">退运管理</div>
            </div>
          </div>
        </div>
      </header>

      <!-- 页面内容（v-show 保留组件状态，切换Tab不丢失进度） -->
      <DashboardPanel v-show="activeTab === 'dashboard'" :active="activeTab === 'dashboard'" @navigate="handleNavigate" />
      <RecognitionPanel v-show="activeTab === 'recognition'"
        :editRecord="pendingEditRecord"
        @confirmed="handleConfirmed" @navigate="handleNavigate" @clearEditRecord="pendingEditRecord = null" />
      <RecordsPanel v-show="activeTab === 'records'" @editRecord="handleEditRecord" @navigate="activeTab = $event" @refresh="handleRefresh" />
    </main>
  </div>
</template>

<script setup>
// 步骤4：组件注册与状态管理
import { ref } from 'vue';
import { Search, Bell, HomeFilled, ScanLine, Document } from './icons';
import DashboardPanel from './components/DashboardPanel.vue';
import RecognitionPanel from './components/RecognitionPanel.vue';
import RecordsPanel from './components/RecordsPanel.vue';

const activeTab = ref('dashboard');

// 跨页面编辑数据传递：RecordsPanel 编辑 -> RecognitionPanel
const pendingEditRecord = ref(null);

const tabs = [
  { key: 'dashboard', label: '工作台', icon: HomeFilled },
  { key: 'recognition', label: '面单识别', icon: ScanLine },
  { key: 'records', label: '退货记录', icon: Document },
];

// 确认写入飞书后跳转到记录页
const handleConfirmed = () => {
  pendingEditRecord.value = null;
  activeTab.value = 'records';
};

// 从识别页/记录页刷新Dashboard
const handleRefresh = (tab) => {
  activeTab.value = tab;
};

// 从记录页点击编辑时，携带记录数据跳转识别页
const handleEditRecord = (row) => {
  pendingEditRecord.value = row;
  activeTab.value = 'recognition';
};

// 识别页导航（回到上传态时清空编辑数据）
const handleNavigate = (tab) => {
  if (tab === 'recognition') {
    pendingEditRecord.value = null;
  }
  activeTab.value = tab;
};
</script>

<style>
/* 步骤5：设计变量（单色系，对齐 Vercel 设计系统） */
:root {
  --color-bg: #ffffff;
  --color-card: #ffffff;
  --color-muted: #f5f5f5;
  --color-fg: #121212;
  --color-fg-muted: #6b6b6b;
  --color-primary: #121212;
  --color-primary-fg: #ffffff;
  --color-secondary: #f5f5f5;
  --color-secondary-fg: #121212;
  --color-accent: #f5f5f5;
  --color-accent-fg: #121212;
  --color-border: #e8e8e8;
  --color-input: #e8e8e8;
  --color-success: #16a34a;
  --color-success-strong: #15803d;
  --color-success-subtle: #f0fdf4;
  --color-error: #dc2626;
  --color-error-strong: #b91c1c;
  --color-error-subtle: #fef2f2;
  --color-sidebar: #fafafa;
  --color-sidebar-fg: #121212;
  --color-sidebar-border: #e8e8e8;
  --color-sidebar-accent: #f5f5f5;
  --color-sidebar-accent-fg: #121212;
  --color-chart-2: #3b82f6;
  --color-chart-3: #2563eb;
  --radius: 8px;
  --spacing: 4px;
  --font-sans: 'Inter', system-ui, -apple-system, 'Microsoft YaHei', sans-serif;
  --font-mono: 'JetBrains Mono', 'SF Mono', 'Consolas', monospace;
  --shadow-sm: 0 1px 2px rgba(0,0,0,0.04);
  --shadow-md: 0 4px 8px rgba(0,0,0,0.06);
  --transition: 150ms ease;

  /* Element Plus 主题覆盖 */
  --el-color-primary: #121212;
  --el-color-primary-light-3: #3b3b3b;
  --el-color-primary-light-5: #6b6b6b;
  --el-color-primary-light-7: #a0a0a0;
  --el-color-primary-light-9: #d4d4d4;
  --el-color-primary-dark-2: #000000;
  --el-border-color: #e8e8e8;
  --el-border-radius-base: 8px;
  --el-font-size-base: 13px;
}

* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: var(--font-sans); background: var(--color-bg); color: var(--color-fg); font-size: 13px; }

/* app-shell 网格 */
.app-shell {
  display: grid;
  grid-template-columns: minmax(216px, 240px) minmax(0, 1fr);
  height: 100vh;
}

/* 侧边栏 */
.sidebar {
  background: var(--color-sidebar);
  border-right: 1px solid var(--color-sidebar-border);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  font-size: 13px;
}

/* 工作区切换 */
.workspace {
  display: flex;
  align-items: center;
  gap: calc(var(--spacing) * 2);
  padding: calc(var(--spacing) * 4) calc(var(--spacing) * 3.5);
}
.ws-avatar {
  width: 28px; height: 28px;
  border-radius: 6px;
  background: var(--color-primary);
  color: var(--color-primary-fg);
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700;
  flex-shrink: 0;
}
.ws-name { font-weight: 600; font-size: 13px; }
.ws-role { font-size: 11px; color: var(--color-fg-muted); }

/* 导航列表 */
.nav-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  padding: 0 calc(var(--spacing) * 2);
}
.nav-item {
  display: flex;
  align-items: center;
  gap: calc(var(--spacing) * 2.5);
  padding: 7px 10px;
  border-radius: var(--radius);
  cursor: pointer;
  border: none;
  background: transparent;
  color: var(--color-fg-muted);
  font-size: 13px;
  font-family: var(--font-sans);
  transition: var(--transition);
  width: 100%;
  text-align: left;
}
.nav-item:hover { background: var(--color-sidebar-accent); color: var(--color-sidebar-accent-fg); }
.nav-item.active { background: var(--color-sidebar-accent); color: var(--color-sidebar-accent-fg); font-weight: 500; }
.nav-item .el-icon { font-size: 16px; flex-shrink: 0; }

/* 分隔线 */
.nav-divider {
  height: 1px;
  background: var(--color-sidebar-border);
  margin: calc(var(--spacing) * 3) calc(var(--spacing) * 3.5);
}

/* 底部用户信息 */
.user-pill {
  margin-top: auto;
  display: flex;
  align-items: center;
  gap: calc(var(--spacing) * 2);
  padding: calc(var(--spacing) * 3) calc(var(--spacing) * 3.5);
  border-top: 1px solid var(--color-sidebar-border);
}
.user-avatar {
  width: 28px; height: 28px;
  border-radius: 50%;
  background: var(--color-secondary);
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 600;
  flex-shrink: 0;
}
.user-name { font-size: 13px; font-weight: 500; }
.user-role { font-size: 11px; color: var(--color-fg-muted); }

/* 主内容区 */
.content {
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

/* 顶栏 */
.topbar {
  position: sticky;
  top: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(var(--spacing) * 3) calc(var(--spacing) * 5);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-bg);
  gap: calc(var(--spacing) * 3);
}

/* 搜索框 */
.search-box {
  display: flex;
  align-items: center;
  gap: calc(var(--spacing) * 2);
  padding: 0 12px;
  height: 34px;
  border: 1px solid var(--color-input);
  border-radius: var(--radius);
  width: 280px;
}
.search-icon { font-size: 16px; color: var(--color-fg-muted); }
.search-input {
  border: none;
  outline: none;
  background: transparent;
  font-size: 13px;
  width: 100%;
  font-family: var(--font-sans);
  color: var(--color-fg);
}
.search-input::placeholder { color: var(--color-fg-muted); }

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  gap: calc(var(--spacing) * 2);
}
.icon-btn {
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: transparent;
  cursor: pointer;
  transition: var(--transition);
}
.icon-btn:hover { background: var(--color-accent); }

/* 用户芯片 */
.user-chip {
  display: flex;
  align-items: center;
  gap: calc(var(--spacing) * 2);
}
.chip-avatar {
  width: 28px; height: 28px;
  border-radius: 50%;
  background: var(--color-secondary);
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 600;
}
.chip-name { font-size: 13px; font-weight: 500; }
.chip-role { font-size: 11px; color: var(--color-fg-muted); }

/* 响应式 */
@media (max-width: 1080px) {
  .search-box { width: 200px; }
}
@media (max-width: 900px) {
  .app-shell { grid-template-columns: 1fr; }
  .sidebar { display: none; }
}
</style>
