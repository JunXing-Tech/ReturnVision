<template>
  <div class="app-layout">
    <header v-if="!isMobile" class="app-header">
      <div class="header-inner">
        <div class="logo" @click="activeTab = 'home'">
          <span class="logo-icon">📦</span>
          <span class="logo-text">退运智录</span>
        </div>
        <nav class="nav-tabs">
          <div v-for="tab in tabs" :key="tab.key"
            :class="['nav-tab', { active: activeTab === tab.key }]"
            @click="activeTab = tab.key">
            <component :is="tab.icon" />
            <span>{{ tab.label }}</span>
          </div>
        </nav>
      </div>
    </header>

    <main class="app-content">
      <HomePanel v-show="activeTab === 'home'" />
      <ProcessPanel v-show="activeTab === 'process'" />
      <RecordsPanel v-show="activeTab === 'records'" />
    </main>

    <nav v-if="isMobile" class="mobile-tabbar">
      <div v-for="tab in tabs" :key="tab.key"
        :class="['mobile-tab', { active: activeTab === tab.key }]"
        @click="activeTab = tab.key">
        <component :is="tab.icon" />
        <span>{{ tab.label }}</span>
      </div>
    </nav>

    <footer v-if="!isMobile" class="app-footer">
      <p>退运智录 ReturnVision · 快递面单OCR智能识别系统</p>
    </footer>
  </div>
</template>

<script setup>
import { useMobile } from './composables/useMobile';
import { useAppState } from './composables/useAppState';
import { HomeFilled, Document, Tickets } from '@element-plus/icons-vue';
import HomePanel from './components/HomePanel.vue';
import ProcessPanel from './components/ProcessPanel.vue';
import RecordsPanel from './components/RecordsPanel.vue';

const { isMobile } = useMobile();
const { activeTab } = useAppState();

const tabs = [
  { key: 'home', label: '主页', icon: HomeFilled },
  { key: 'process', label: '流程展示', icon: Tickets },
  { key: 'records', label: '记录列表', icon: Document },
];
</script>

<style>
:root {
  --color-primary: #0d9488;
  --color-primary-light: #14b8a6;
  --color-primary-dark: #0f766e;
  --color-bg: #f8fafc;
  --color-bg-secondary: #f1f5f9;
  --color-text: #1e293b;
  --color-text-secondary: #64748b;
  --color-text-muted: #94a3b8;
  --color-border: #e2e8f0;
  --color-success: #22c55e;
  --color-warning: #eab308;
  --color-error: #ef4444;
  --shadow-sm: 0 1px 3px rgba(0,0,0,0.05);
  --shadow-md: 0 4px 12px -2px rgba(0,0,0,0.08);
  --shadow-lg: 0 12px 24px -4px rgba(0,0,0,0.12);
  --radius-sm: 6px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 16px;
  --transition: 200ms ease-out;
  --el-color-primary: #0d9488;
  --el-color-primary-light-3: #2dd4bf;
  --el-color-primary-light-5: #5eead4;
  --el-color-primary-light-7: #99f6e4;
  --el-color-primary-light-9: #ccfbf1;
  --el-color-primary-dark-2: #0f766e;
}
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: system-ui, "Microsoft YaHei", sans-serif; background: var(--color-bg); color: var(--color-text); }
.app-layout { min-height: 100vh; display: flex; flex-direction: column; }
.app-header { position: sticky; top: 0; z-index: 100; background: rgba(255,255,255,0.85); backdrop-filter: blur(20px) saturate(180%); border-bottom: 1px solid var(--color-border); }
.header-inner { max-width: 1200px; margin: 0 auto; padding: 0 24px; height: 60px; display: flex; align-items: center; justify-content: space-between; }
.logo { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.logo-icon { font-size: 24px; }
.logo-text { font-size: 18px; font-weight: 700; color: var(--color-text); }
.nav-tabs { display: flex; gap: 4px; }
.nav-tab { display: flex; align-items: center; gap: 6px; padding: 8px 16px; border-radius: var(--radius-md); cursor: pointer; color: var(--color-text-secondary); transition: var(--transition); font-size: 14px; font-weight: 500; }
.nav-tab:hover { background: var(--color-bg-secondary); color: var(--color-text); }
.nav-tab.active { background: var(--color-primary); color: #fff; }
.app-content { flex: 1; max-width: 1200px; width: 100%; margin: 0 auto; padding: 24px; }
.mobile-tabbar { position: fixed; bottom: 0; left: 0; right: 0; z-index: 100; display: flex; background: rgba(255,255,255,0.95); backdrop-filter: blur(20px); border-top: 1px solid var(--color-border); padding: 8px 0 env(safe-area-inset-bottom, 8px); }
.mobile-tab { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 8px 0; cursor: pointer; color: var(--color-text-muted); transition: var(--transition); font-size: 11px; }
.mobile-tab.active { color: var(--color-primary); }
.mobile-tab .el-icon { font-size: 22px; }
.app-footer { text-align: center; padding: 24px; color: var(--color-text-muted); font-size: 13px; border-top: 1px solid var(--color-border); margin-top: 40px; }
@media (max-width: 768px) { .app-content { padding: 16px 16px 80px; } }
</style>
