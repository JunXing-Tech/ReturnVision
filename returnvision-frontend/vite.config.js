import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 退运智录前端配置
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
