<template>
  <!-- 登录页（未认证时显示） -->
  <LoginPanel v-if="!isAuthenticated" @login-success="handleLoginSuccess" />

  <!-- 步骤1：app-shell 网格布局（侧边栏 + 主内容区） -->
  <div v-else class="app-shell">
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
        <button v-for="tab in visibleTabs" :key="tab.key"
          :class="['nav-item', { active: activeTab === tab.key }]"
          @click="activeTab = tab.key">
          <component :is="tab.icon" />
          <span>{{ tab.label }}</span>
        </button>
      </nav>

      <!-- 分隔线 -->
      <div class="nav-divider"></div>

      <!-- 底部用户信息 + 下拉菜单（个人信息 / 退出登录） -->
      <div class="user-menu-wrapper">
        <button class="user-pill" @click="toggleUserMenu" :class="{ active: showUserMenu }">
          <div class="user-avatar">{{ userInitials }}</div>
          <div class="user-info">
            <div class="user-name">{{ user?.display_name || user?.username || '运营员' }}</div>
            <div class="user-role">{{ userRolesText }}</div>
          </div>
          <el-icon class="user-chevron" :class="{ rotated: showUserMenu }"><ChevronDown /></el-icon>
        </button>

        <!-- 下拉菜单 -->
        <div v-if="showUserMenu" class="user-dropdown" @click.stop>
          <button class="dropdown-item" @click="goProfile">
            <el-icon><UserCircle /></el-icon>
            <span>个人中心</span>
          </button>
          <div class="dropdown-divider"></div>
          <button class="dropdown-item dropdown-danger" @click="handleLogout">
            <el-icon><LogoutIcon /></el-icon>
            <span>退出登录</span>
          </button>
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
          <!-- 步骤2.1：主题切换按钮 -->
          <button class="icon-btn theme-toggle" @click="toggleTheme" :aria-label="isDark ? '切换到浅色' : '切换到暗色'">
            <el-icon><Moon v-if="!isDark" /><Sun v-else /></el-icon>
          </button>
          <button class="icon-btn">
            <el-icon><Bell /></el-icon>
          </button>
          <div class="user-chip">
            <div class="chip-avatar">{{ userInitials }}</div>
            <div class="chip-info">
              <div class="chip-name">{{ user?.display_name || user?.username || '运营员' }}</div>
              <div class="chip-role">{{ userRolesText }}</div>
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
      <UserManagePanel v-show="activeTab === 'users'" />
      <ProfilePanel v-show="activeTab === 'profile'" />
      <AuditLogPanel v-show="activeTab === 'audit'" />
    </main>
  </div>
</template>

<script setup>
// 步骤4：组件注册与状态管理
import { ref, computed, onMounted } from 'vue';
import { Search, Bell, HomeFilled, ScanLine, Document, Sun, Moon, X, UserFilled, ChevronDown, UserCircle, LogoutIcon, ClipboardList } from './icons';
import { useTheme } from './composables/useTheme';
import { useAuth } from './composables/useAuth';
import api from './api';
import LoginPanel from './components/LoginPanel.vue';
import DashboardPanel from './components/DashboardPanel.vue';
import RecognitionPanel from './components/RecognitionPanel.vue';
import RecordsPanel from './components/RecordsPanel.vue';
import UserManagePanel from './components/UserManagePanel.vue';
import ProfilePanel from './components/ProfilePanel.vue';
import AuditLogPanel from './components/AuditLogPanel.vue';

const activeTab = ref('dashboard');

// 步骤4.1：主题切换（首次跟随系统，用户切换后持久化）
const { isDark, toggleTheme } = useTheme();

// 步骤4.2：鉴权状态
const { isAuthenticated, user, clear } = useAuth();

// 跨页面编辑数据传递：RecordsPanel 编辑 -> RecognitionPanel
const pendingEditRecord = ref(null);

// 步骤4.2.1：用户下拉菜单状态
const showUserMenu = ref(false);
const toggleUserMenu = () => { showUserMenu.value = !showUserMenu.value; };
const goProfile = () => {
  activeTab.value = 'profile';
  showUserMenu.value = false;
};

// 步骤4.3：用户角色判断 -- 客服不显示工作台 Tab，仅管理员显示用户管理 Tab
const visibleTabs = computed(() => {
  const allTabs = [
    { key: 'dashboard', label: '工作台', icon: HomeFilled, roles: ['SUPERVISOR', 'ADMIN'] },
    { key: 'recognition', label: '面单识别', icon: ScanLine, roles: ['STAFF', 'SUPERVISOR', 'ADMIN'] },
    { key: 'records', label: '退货记录', icon: Document, roles: ['STAFF', 'SUPERVISOR', 'ADMIN'] },
    { key: 'users', label: '用户管理', icon: UserFilled, roles: ['ADMIN'] },
    { key: 'audit', label: '审计日志', icon: ClipboardList, roles: ['SUPERVISOR', 'ADMIN'] },
    { key: 'profile', label: '个人中心', icon: UserCircle, roles: ['STAFF', 'SUPERVISOR', 'ADMIN'] },
  ];
  const userRoles = user.value?.roles || [];
  return allTabs.filter(tab => tab.roles.some(r => userRoles.includes(r)));
});

// 步骤4.4：用户首字母 + 角色文本
const userInitials = computed(() => {
  const name = user.value?.display_name || user.value?.username || 'RV';
  return name.slice(0, 2).toUpperCase();
});

const userRolesText = computed(() => {
  const roles = user.value?.roles || [];
  const roleMap = { STAFF: '客服', SUPERVISOR: '主管', ADMIN: '管理员' };
  return roles.map(r => roleMap[r] || r).join('、') || '退运管理';
});

const tabs = visibleTabs;

// 登录成功后默认跳第一个可见 Tab
const handleLoginSuccess = () => {
  if (visibleTabs.value.length > 0) {
    activeTab.value = visibleTabs.value[0].key;
  }
};

// 登出
const handleLogout = async () => {
  showUserMenu.value = false;
  try {
    await api.logout();
  } catch (e) {
    // 登出失败也清本地状态
  }
  clear();
};

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

// 步骤4.5：挂载时检查 URL 是否含飞书回调 code
onMounted(async () => {
  const urlParams = new URLSearchParams(window.location.search);
  const code = urlParams.get('code');
  const state = urlParams.get('state');
  if (code) {
    // 飞书 OAuth 回调
    try {
      const resp = await api.feishuCallback(code, state);
      const { access_token, refresh_token, userInfo } = resp.data.data;
      const { setTokens } = useAuth();
      setTokens(access_token, refresh_token, userInfo);
      // 清理 URL 参数
      window.history.replaceState({}, document.title, window.location.pathname);
    } catch (err) {
      console.error('飞书登录失败', err);
    }
  }
});
</script>

<style>
/* 步骤5：设计变量（Vercel 单色系双主题，v2.1）
   默认浅色（运营场景优先），暗色通过 :root.dark 覆盖。
   组件一律用 var(--color-*)，不写死色值，切换主题零改动。 */
:root {
  /* 基础面（浅色默认） */
  --color-bg: #ffffff;
  --color-card: #ffffff;
  --color-muted: #f5f5f5;
  --color-fg: #0a0a0a;
  --color-fg-muted: #6b6b6b;

  /* 主色（深底浅字按钮） */
  --color-primary: #0a0a0a;
  --color-primary-fg: #ffffff;
  --color-primary-hover: #2a2a2a;
  --color-secondary: #f5f5f5;
  --color-secondary-fg: #0a0a0a;
  --color-accent: #f5f5f5;
  --color-accent-fg: #0a0a0a;

  /* 边框/输入 */
  --color-border: #e8e8e8;
  --color-input: #e8e8e8;

  /* 状态色（小色点/文字色，不整块填充） */
  --color-success: #16a34a;
  --color-success-strong: #15803d;
  --color-success-subtle: #f0fdf4;
  --color-error: #dc2626;
  --color-error-strong: #b91c1c;
  --color-error-subtle: #fef2f2;
  --color-warning: #d97706;
  --color-warning-strong: #b45309;
  --color-warning-subtle: #fffbeb;

  /* 侧边栏 */
  --color-sidebar: #fafafa;
  --color-sidebar-fg: #0a0a0a;
  --color-sidebar-border: #e8e8e8;
  --color-sidebar-accent: #f5f5f5;
  --color-sidebar-accent-fg: #0a0a0a;

  /* 图表色（单色蓝阶） */
  --color-chart-1: #93c5fd;
  --color-chart-2: #3b82f6;
  --color-chart-3: #2563eb;
  --color-chart-4: #1d4ed8;
  --color-chart-5: #1e40af;

  /* 尺度（两主题共享） */
  --radius: 8px;
  --spacing: 4px;
  --font-sans: 'Inter', 'Geist', system-ui, -apple-system, 'Microsoft YaHei', sans-serif;
  --font-mono: 'Geist Mono', 'JetBrains Mono', 'SF Mono', 'Consolas', monospace;
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.04);
  --shadow-md: 0 4px 8px rgba(0, 0, 0, 0.06);
  --transition: 150ms ease;

  /* Element Plus 主题覆盖（浅色） */
  --el-color-primary: #0a0a0a;
  --el-color-primary-light-3: #3b3b3b;
  --el-color-primary-light-5: #6b6b6b;
  --el-color-primary-light-7: #a0a0a0;
  --el-color-primary-light-9: #d4d4d4;
  --el-color-primary-dark-2: #000000;
  --el-color-success: #16a34a;
  --el-color-danger: #dc2626;
  --el-color-warning: #d97706;
  --el-bg-color: #ffffff;
  --el-bg-color-page: #ffffff;
  --el-bg-color-overlay: #ffffff;
  --el-text-color-primary: #0a0a0a;
  --el-text-color-regular: #4b4b4b;
  --el-text-color-secondary: #6b6b6b;
  --el-text-color-placeholder: #9b9b9b;
  --el-border-color: #e8e8e8;
  --el-border-color-light: #f0f0f0;
  --el-border-color-lighter: #f5f5f5;
  --el-border-color-extra-light: #fafafa;
  --el-border-color-dark: #d4d4d4;
  --el-fill-color: #f5f5f5;
  --el-fill-color-light: #f7f7f7;
  --el-fill-color-lighter: #fafafa;
  --el-fill-color-extra-light: #fcfcfc;
  --el-fill-color-blank: #ffffff;
  --el-mask-color: rgba(0, 0, 0, 0.5);
  --el-box-shadow: 0 4px 8px rgba(0, 0, 0, 0.06);
  --el-box-shadow-light: 0 1px 2px rgba(0, 0, 0, 0.04);
  --el-border-radius-base: 8px;
  --el-font-size-base: 13px;
}

/* 暗色主题：覆盖所有色值，尺度变量复用 */
:root.dark {
  /* 基础面（暗色） */
  --color-bg: #0a0a0a;
  --color-card: #111111;
  --color-muted: #1a1a1a;
  --color-fg: #fafafa;
  --color-fg-muted: #a1a1a1;

  /* 主色（反相：浅底深字按钮） */
  --color-primary: #fafafa;
  --color-primary-fg: #171717;
  --color-primary-hover: #e0e0e0;
  --color-secondary: #1a1a1a;
  --color-secondary-fg: #fafafa;
  --color-accent: #1a1a1a;
  --color-accent-fg: #fafafa;

  /* 边框/输入 */
  --color-border: #262626;
  --color-input: #262626;

  /* 状态色（暗色下提高亮度，保证对比度） */
  --color-success: #62d178;
  --color-success-strong: #3fa658;
  --color-success-subtle: #1a2e1f;
  --color-error: #ff6166;
  --color-error-strong: #e23a3f;
  --color-error-subtle: #2e1a1c;
  --color-warning: #f5a623;
  --color-warning-strong: #c4831a;
  --color-warning-subtle: #2e2418;

  /* 侧边栏 */
  --color-sidebar: #0a0a0a;
  --color-sidebar-fg: #fafafa;
  --color-sidebar-border: #1a1a1a;
  --color-sidebar-accent: #1a1a1a;
  --color-sidebar-accent-fg: #fafafa;

  /* 图表色（暗色下提亮） */
  --color-chart-1: #91c5ff;
  --color-chart-2: #3a81f6;
  --color-chart-3: #2563ef;
  --color-chart-4: #1a4eda;
  --color-chart-5: #1f3fad;

  /* 阴影（暗色下加重） */
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.3);
  --shadow-md: 0 4px 8px rgba(0, 0, 0, 0.4);

  /* Element Plus 主题覆盖（暗色） */
  --el-color-primary: #fafafa;
  --el-color-primary-light-3: #d4d4d4;
  --el-color-primary-light-5: #a0a0a0;
  --el-color-primary-light-7: #6b6b6b;
  --el-color-primary-light-9: #3b3b3b;
  --el-color-primary-dark-2: #ffffff;
  --el-color-success: #62d178;
  --el-color-danger: #ff6166;
  --el-color-warning: #f5a623;
  --el-bg-color: #111111;
  --el-bg-color-page: #0a0a0a;
  --el-bg-color-overlay: #111111;
  --el-text-color-primary: #fafafa;
  --el-text-color-regular: #c4c4c4;
  --el-text-color-secondary: #a1a1a1;
  --el-text-color-placeholder: #737373;
  --el-border-color: #262626;
  --el-border-color-light: #1a1a1a;
  --el-border-color-lighter: #1a1a1a;
  --el-border-color-extra-light: #1a1a1a;
  --el-border-color-dark: #262626;
  --el-fill-color: #1a1a1a;
  --el-fill-color-light: #1a1a1a;
  --el-fill-color-lighter: #111111;
  --el-fill-color-extra-light: #111111;
  --el-fill-color-blank: #0a0a0a;
  --el-mask-color: rgba(0, 0, 0, 0.7);
  --el-box-shadow: 0 4px 8px rgba(0, 0, 0, 0.4);
  --el-box-shadow-light: 0 1px 2px rgba(0, 0, 0, 0.3);
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

/* 底部用户信息 + 下拉菜单 */
.user-menu-wrapper {
  position: relative;
  margin-top: auto;
}
.user-pill {
  width: 100%;
  display: flex;
  align-items: center;
  gap: calc(var(--spacing) * 2);
  padding: calc(var(--spacing) * 3) calc(var(--spacing) * 3.5);
  border-top: 1px solid var(--color-sidebar-border);
  background: transparent;
  border-left: none;
  border-right: none;
  border-bottom: none;
  cursor: pointer;
  color: var(--color-fg);
  text-align: left;
}
.user-pill:hover, .user-pill.active {
  background: var(--color-secondary);
}
.user-avatar {
  width: 28px; height: 28px;
  border-radius: 50%;
  background: var(--color-secondary);
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 600;
  flex-shrink: 0;
}
.user-info {
  flex: 1;
  min-width: 0;
}
.user-name { font-size: 13px; font-weight: 500; }
.user-role { font-size: 11px; color: var(--color-fg-muted); }
.user-chevron {
  transition: transform 0.2s;
  color: var(--color-fg-muted);
  flex-shrink: 0;
}
.user-chevron.rotated {
  transform: rotate(180deg);
}

/* 下拉菜单 */
.user-dropdown {
  position: absolute;
  bottom: calc(100% + 4px);
  left: calc(var(--spacing) * 2);
  right: calc(var(--spacing) * 2);
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  overflow: hidden;
  z-index: 100;
}
.dropdown-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: transparent;
  border: none;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-fg);
  text-align: left;
}
.dropdown-item:hover {
  background: var(--color-secondary);
}
.dropdown-item.dropdown-danger {
  color: #ef4444;
}
.dropdown-item.dropdown-danger:hover {
  background: rgba(239, 68, 68, 0.08);
}
.dropdown-divider {
  height: 1px;
  background: var(--color-border);
  margin: 0;
}

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
