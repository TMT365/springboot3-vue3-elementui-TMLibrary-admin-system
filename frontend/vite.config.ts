import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  
  plugins: [
    vue(),
    // 自动按需导入 Element Plus 组件（无需 import 即可在模板中使用 <el-button>）
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  resolve: {
    // 让 `@/foo` 写法能跑（tsconfig.app.json 已配 paths，运行时也要配套）
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // dev 时把 /api/* 反向代理到 Spring Boot 后端
    proxy: {
      '/api': {
        target: 'http://172.21.224.134:8080',
        changeOrigin: true,
      },
    },
    host: '0.0.0.0',
  },
})