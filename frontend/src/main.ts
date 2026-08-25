// 导入createApp函数,用于创建Vue应用实例
import { createApp } from 'vue'
// 导入createPinia函数,用于创建Pinia状态管理实例
import { createPinia } from 'pinia'
// 导入Element Plus组件库的样式文件
import 'element-plus/dist/index.css'
// 导入自定义的主题样式文件和全局样式文件
import './styles/theme.css'
import './style.css'
// 导入App.vue组件,作为应用的根组件
import App from './App.vue'
// 导入路由实例,用于管理应用的路由
import router from './router'
// 导入initTheme函数,用于初始化应用的主题设置
import { initTheme } from './composables/useTheme'
// 导入vReveal指令,用于实现元素的揭示效果
import { vReveal } from './directives/reveal'

const app = createApp(App)
app.directive('reveal', vReveal)
app.use(createPinia())
app.use(router)
// 在挂载前初始化主题,避免 SPA 渲染首帧出现主题闪(尤其从 /admin/* 切回 /)
initTheme()
app.mount('#app')