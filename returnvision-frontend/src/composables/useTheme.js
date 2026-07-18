// 步骤1：主题切换 composable
// 职责：管理 light/dark 主题，首次访问跟随系统偏好，用户切换后持久化到 localStorage
// 用法：const { isDark, toggleTheme } = useTheme();

import { ref, watch } from 'vue';

const STORAGE_KEY = 'returnvision-theme';
const isDark = ref(false);
let initialized = false;

// 步骤2：读取已保存的主题偏好（localStorage 优先，否则跟随系统 prefers-color-scheme）
const resolveInitialTheme = () => {
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved === 'light' || saved === 'dark') {
    return saved === 'dark';
  }
  // 未保存过，跟随系统偏好
  return window.matchMedia('(prefers-color-scheme: dark)').matches;
};

// 步骤3：应用主题到 <html> 根节点（通过 class 切换，触发 :root.dark CSS 覆盖）
const applyTheme = (dark) => {
  const root = document.documentElement;
  if (dark) {
    root.classList.add('dark');
  } else {
    root.classList.remove('dark');
  }
};

// 步骤4：初始化（仅执行一次，避免重复绑定）
const init = () => {
  if (initialized) return;
  initialized = true;
  isDark.value = resolveInitialTheme();
  applyTheme(isDark.value);

  // 步骤5：监听系统主题变化（仅当用户未主动选择时跟随）
  const mql = window.matchMedia('(prefers-color-scheme: dark)');
  mql.addEventListener('change', (e) => {
    // 用户已主动选择过则不跟随系统
    if (localStorage.getItem(STORAGE_KEY)) return;
    isDark.value = e.matches;
    applyTheme(isDark.value);
  });

  // 步骤6：响应 isDark 变化，同步 DOM 与 localStorage
  watch(isDark, (dark) => {
    applyTheme(dark);
    localStorage.setItem(STORAGE_KEY, dark ? 'dark' : 'light');
  });
};

// 步骤7：切换主题
const toggleTheme = () => {
  isDark.value = !isDark.value;
};

export function useTheme() {
  init();
  return { isDark, toggleTheme };
}
