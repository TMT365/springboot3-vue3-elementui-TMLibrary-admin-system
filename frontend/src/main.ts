// 导入createApp函数,用于创建Vue应用实例
import { createApp } from 'vue'
// 导入createPinia函数,用于创建Pinia状态管理实例
import { createPinia } from 'pinia'
// 导入Element Plus组件库的样式文件
import 'element-plus/dist/index.css'
// Element Plus 暗色主题变量(在 html.dark 下生效,由 useTheme 同步切换)
//
// 不引入的话:EP 组件的 --el-fill-color-blank / --el-text-color-* 永远停在
// 亮色主题的值(表格底 #fff、文字 #606266)—— 项目自己的 token 已经变暗,
// 两边打架就会出现"白底白字"或"深底深字"。
import 'element-plus/theme-chalk/dark/css-vars.css'
// 导入Element Plus图标集 —— 需要全局注册,见下方 app.component 循环
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
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

/**
 * 全局注册 Element Plus 图标。
 *
 * 为什么必须手动注册:unplugin-vue-components 的 ElementPlusResolver 只解析
 * `^El[A-Z]` 开头的组件名(见其 resolveComponent 实现),`<Moon />`、
 * `<UserFilled />`、`<ArrowDown />` 这类图标名不在其列 —— 不注册的话
 * 编译产物是 `_resolveComponent("Moon")`,运行时解析不到,渲染成一个空元素,
 * 图标全部不可见(侧栏、下拉箭头、头像、Landing 功能图标…)。
 *
 * 注册后 `<component :is="'Reading'" />` 这种动态字符串写法也能解析。
 */
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.directive('reveal', vReveal)
app.use(createPinia())
app.use(router)
// 在挂载前初始化主题,避免 SPA 渲染首帧出现主题闪(尤其从 /admin/* 切回 /)
initTheme()
app.mount('#app')