<template>
  <!-- 登录页（未认证时显示） -->
  <LoginPanel v-if="!isAuthenticated" ref="loginPanelRef" @login-success="handleLoginSuccess" />

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

      <!-- 导航列表（按分组渲染小字标题） -->
      <nav class="nav-list">
        <div v-for="grp in groupedTabs" :key="grp.label" class="nav-group">
          <div class="nav-group-label">{{ grp.label }}</div>
          <button v-for="tab in grp.items" :key="tab.key"
            :class="['nav-item', { active: activeTab === tab.key }]"
            @click="activeTab = tab.key">
            <component :is="tab.icon" />
            <span>{{ tab.label }}</span>
          </button>
        </div>
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
      <DictPanel v-show="activeTab === 'dict'" />
      <ReportPanel v-show="activeTab === 'report'" />
      <ProfilePanel v-show="activeTab === 'profile'" />
      <AuditLogPanel v-show="activeTab === 'audit'" />
      <OcrStatsPanel v-show="activeTab === 'ocrstats'" />
    </main>
  </div>
</template>

<script setup>
// 步骤4：组件注册与状态管理
import { ref, computed, onMounted } from 'vue';
import { Search, Bell, HomeFilled, ScanLine, Document, X, UserFilled, ChevronDown, UserCircle, LogoutIcon, ClipboardList, Download, ChartColumn } from './icons';
import { useAuth } from './composables/useAuth';
import api from './api';
import LoginPanel from './components/LoginPanel.vue';
import DashboardPanel from './components/DashboardPanel.vue';
import RecognitionPanel from './components/RecognitionPanel.vue';
import RecordsPanel from './components/RecordsPanel.vue';
import UserManagePanel from './components/UserManagePanel.vue';
import ProfilePanel from './components/ProfilePanel.vue';
import AuditLogPanel from './components/AuditLogPanel.vue';
import OcrStatsPanel from './components/OcrStatsPanel.vue';
import DictPanel from './components/DictPanel.vue';
import ReportPanel from './components/ReportPanel.vue';

const activeTab = ref('dashboard');

// 步骤4.2：鉴权状态
const { isAuthenticated, user, clear } = useAuth();

// 飞书登录回调失败时，调 LoginPanel 的 setError 显示错误
const loginPanelRef = ref(null);

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
    { key: 'dashboard', label: '工作台', icon: HomeFilled, roles: ['SUPERVISOR', 'ADMIN'], group: '退货录入工作台' },
    { key: 'recognition', label: '面单识别', icon: ScanLine, roles: ['STAFF', 'SUPERVISOR', 'ADMIN'], group: '业务导航' },
    { key: 'records', label: '退货记录', icon: Document, roles: ['STAFF', 'SUPERVISOR', 'ADMIN'], group: '业务导航' },
    { key: 'report', label: '退货报表', icon: ChartColumn, roles: ['STAFF', 'SUPERVISOR', 'ADMIN'], group: '业务导航' },
    { key: 'users', label: '用户管理', icon: UserFilled, roles: ['ADMIN'], group: '系统管理' },
    { key: 'dict', label: '退货字典', icon: ClipboardList, roles: ['ADMIN'], group: '系统管理' },
    { key: 'audit', label: '审计日志', icon: ClipboardList, roles: ['SUPERVISOR', 'ADMIN'], group: '系统管理' },
    { key: 'profile', label: '个人中心', icon: UserCircle, roles: ['STAFF', 'SUPERVISOR', 'ADMIN'], group: '系统管理' },
  ];
  const userRoles = user.value?.roles || [];
  return allTabs.filter(tab => tab.roles.some(r => userRoles.includes(r)));
});

// 步骤4.3.1：按 group 分组导航项（供模板渲染分组小字标题）
const groupedTabs = computed(() => {
  const groups = [];
  const groupMap = new Map();
  visibleTabs.value.forEach(tab => {
    if (!groupMap.has(tab.group)) {
      groupMap.set(tab.group, []);
      groups.push(tab.group);
    }
    groupMap.get(tab.group).push(tab);
  });
  return groups.map(g => ({ label: g, items: groupMap.get(g) }));
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
      const { access_token, refresh_token, user } = resp.data.data;
      const { setTokens } = useAuth();
      setTokens(access_token, refresh_token, user);
      // 清理 URL 参数
      window.history.replaceState({}, document.title, window.location.pathname);
    } catch (err) {
      // F01 修复：把后端错误信息显示到登录页，不再只 console.error
      const msg = err.response?.data?.msg || '飞书登录失败，请重试或联系管理员';
      loginPanelRef.value?.setError(msg);
      console.error('飞书登录失败', err);
      // 清理 URL 参数，避免刷新时重复回调
      window.history.replaceState({}, document.title, window.location.pathname);
    }
  }
});
</script>

<style>
/* 步骤5：设计变量（源力 Volcengine 设计系统，v3.0）
   品牌蓝主色 + 中文优先字体 + 完整状态色 ramp + 5 色图表 + 5 级阴影。
   固定浅色主题（运营场景），无暗色模式。
   组件一律用 var(--color-*)，不写死色值。
   token 命名保持 --color-* 以兼容现有组件，色值映射源力 --yuanli-* 体系。 */
:root {
  /* 基础面（浅色默认） */
  --color-bg: #ffffff;
  --color-card: #ffffff;
  --color-muted: #f6f8fa;
  --color-fg: #0c0d0e;
  --color-fg-muted: #737a87;

  /* 主色（品牌蓝 #1664ff） */
  --color-primary: #1664ff;
  --color-primary-fg: #ffffff;
  --color-primary-hover: #387bff;
  --color-secondary: #f6f8fa;
  --color-secondary-fg: #0c0d0e;
  --color-accent: #ebf1ff;
  --color-accent-fg: #114ab9;

  /* 边框/输入 */
  --color-border: #dde2e9;
  --color-input: #dde2e9;

  /* 状态色（浅底徽章 + 强色文字，成对使用） */
  --color-success: #309256;
  --color-success-strong: #2a814b;
  --color-success-subtle: #eef9f1;
  --color-error: #ee3f38;
  --color-error-strong: #d7312a;
  --color-error-subtle: #fdf5f5;
  --color-warning: #bd7e00;
  --color-warning-strong: #d08d06;
  --color-warning-subtle: #fef8eb;

  /* 侧边栏 */
  --color-sidebar: #fcfdfe;
  --color-sidebar-fg: #0c0d0e;
  --color-sidebar-border: #eaedf1;
  --color-sidebar-accent: #f6f8fa;
  --color-sidebar-accent-fg: #0c0d0e;

  /* 图表色（5 色区分退货趋势系列） */
  --color-chart-1: #387bff;
  --color-chart-2: #7ccd94;
  --color-chart-3: #f0a50f;
  --color-chart-4: #ff706d;
  --color-chart-5: #86909c;

  /* 焦点环（品牌蓝） */
  --color-ring: #387bff;

  /* 尺度（两主题共享） */
  --radius: 8px;
  --spacing: 4px;
  --font-sans: 'PingFang SC', 'Microsoft YaHei', 'Roboto', 'Helvetica Neue', Arial, sans-serif;
  --font-mono: 'SFMono-Regular', 'SF Mono', 'Roboto Mono', 'Consolas', monospace;
  --shadow-sm: 0 1px 2px 0 rgba(12, 13, 14, 0.05), 0 1px 1px 0 rgba(12, 13, 14, 0.03);
  --shadow-md: 0 4px 8px -2px rgba(12, 13, 14, 0.07), 0 2px 4px -1px rgba(12, 13, 14, 0.04);
  --shadow-lg: 0 12px 20px -6px rgba(12, 13, 14, 0.08), 0 4px 8px -2px rgba(12, 13, 14, 0.04);
  --transition: 150ms ease;

  /* Element Plus 主题覆盖（浅色，品牌蓝） */
  --el-color-primary: #1664ff;
  --el-color-primary-light-3: #387bff;
  --el-color-primary-light-5: #6695ff;
  --el-color-primary-light-7: #b3cdff;
  --el-color-primary-light-9: #ebf1ff;
  --el-color-primary-dark-2: #114ab9;
  --el-color-success: #309256;
  --el-color-danger: #ee3f38;
  --el-color-warning: #bd7e00;
  --el-bg-color: #ffffff;
  --el-bg-color-page: #ffffff;
  --el-bg-color-overlay: #ffffff;
  --el-text-color-primary: #0c0d0e;
  --el-text-color-regular: #42464e;
  --el-text-color-secondary: #737a87;
  --el-text-color-placeholder: #c7ccd6;
  --el-border-color: #dde2e9;
  --el-border-color-light: #eaedf1;
  --el-border-color-lighter: #f6f8fa;
  --el-border-color-extra-light: #fafbfc;
  --el-border-color-dark: #d1d5da;
  --el-fill-color: #f6f8fa;
  --el-fill-color-light: #fafbfc;
  --el-fill-color-lighter: #fcfdfe;
  --el-fill-color-extra-light: #ffffff;
  --el-fill-color-blank: #ffffff;
  --el-mask-color: rgba(12, 13, 14, 0.5);
  --el-box-shadow: 0 4px 8px -2px rgba(12, 13, 14, 0.07), 0 2px 4px -1px rgba(12, 13, 14, 0.04);
  --el-box-shadow-light: 0 1px 2px 0 rgba(12, 13, 14, 0.05);
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
  gap: calc(var(--spacing) * 3);
  padding: 0 calc(var(--spacing) * 2);
}
/* 分组小字标题 */
.nav-group { display: flex; flex-direction: column; gap: 1px; }
.nav-group-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  color: var(--color-fg-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  padding: 0 12px;
  margin-bottom: 4px;
  opacity: 0.7;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: calc(var(--spacing) * 2.5);
  padding: 8px 12px;
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
  position: relative;
}
.nav-item:hover { background: var(--color-sidebar-accent); color: var(--color-fg); }
.nav-item.active {
  background: var(--color-accent);
  color: var(--color-primary);
  font-weight: 500;
}
/* 激活态品牌蓝左指示条（与设计稿一致） */
.nav-item.active::before {
  content: '';
  position: absolute;
  left: -8px;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 0 2px 2px 0;
  background: var(--color-primary);
}
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
