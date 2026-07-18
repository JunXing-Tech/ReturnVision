import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'

// 步骤1：提前应用主题，避免挂载前出现主题闪屏（FOUC）
// 逻辑与 useTheme.js 保持一致：localStorage 优先，否则跟随系统偏好
;(() => {
  const saved = localStorage.getItem('returnvision-theme')
  const dark = saved
    ? saved === 'dark'
    : window.matchMedia('(prefers-color-scheme: dark)').matches
  if (dark) document.documentElement.classList.add('dark')
})()

const app = createApp(App)
app.use(ElementPlus)
app.mount('#app')
