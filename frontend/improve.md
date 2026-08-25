# 前端改进计划 — Landing + 全站 UI

> **本文档由 AI 协助整理**。原稿存在大量冗余(同一 CSS 属性反复描述)、语言混杂(简繁中英阿混用)、个别条目互相矛盾(如「响应式」vs「搜索框固定 400px」),这里在保留意图的前提下做了清理、补充、解决矛盾。
>
> **v2 (2026-08-22)**: §1–§6 是设计意图(不变),§7 是当前 12 条实施 backlog。第 9 条经讨论改为「移除搜索框」。

---

## 0. 工作流

开始工作前,**加载并参考**以下 skills 的设计指引:

- `frontend-design`(视觉方向、避免 AI 通用美学)
- `frontend-skill`(landing / app 视觉规范)
- `make-interfaces-feel-better`(细节抛光)
- `ui-ux-pro-max`(色彩 / 字体 / 布局智能)

---

## 1. 目标

把项目根路由 `/` 改造为**公开 landing 页**(项目介绍 + 欢迎页),而不是直接进登录后台。

**当前状况**

- `/` 挂 AdminLayout,自动 redirect 到 `/dashboard`
- 没登录 → 守卫踢到 `/login`
- 已登录 → 直接进 dashboard

**改造后**

- `/` → `Landing.vue`(**公开**,未登录也能访问)
- 顶部 nav 提供「登录」入口 → 跳 `/login`
- 已登录用户访问 `/` 仍看 Landing,但 nav 额外展示「进入管理后台」
- AdminLayout 改挂到 `/admin` 下,原 `/dashboard`、`/books` 等路径全部变成 `/admin/dashboard`、`/admin/books`...

✅ **已实装**:`/admin/*` 路由结构、四个 landing 子组件。

---

## 2. 视觉设计

### 2.1 风格方向

- **Editorial / 书卷气**(贴合「图书馆管理系统」主题)
- **绿色作为 sharp accent 使用**,**不要**铺满整个页面
- 颜色基调:浅色模式用暖米白(`#FAF8F3` 一类的非纯白色),深色模式用炭灰(`#1A1A1A`)
- **避免**:紫色渐变、纯白背景、Inter 字体居中、过于均匀的卡片网格——典型 AI 通用美学

### 2.2 配色(CSS 变量)

| 用途 | 颜色 |
|------|------|
| 主色调(accent,仅强调) | `#4CAF50`(Material Green 500) |
| 强调色 | `#FFC107`(Material Amber 500) |
| 浅色背景 | `#F5F5F5`(基础色),大面积使用 `#FAF8F3` 暖米白 |
| 浅色文字 | `#212121` |
| 夜间背景 | `#1A1A1A`(不用纯黑) |
| 夜间文字 | `#F5F5F5` |
| 灰色文本 / 占位符 | `#9E9E9E` |

### 2.3 字体(Google Fonts)

- **Display**:Fraunces(特色 serif,**不**用 Inter / Space Grotesk)
- **Body**:Manrope(圆润 sans,读感好)

通过 `<link>` 引入 + `font-display: swap`(避免阻塞渲染)。

---

## 3. 页面结构

### 3.1 导航栏(顶部 sticky)

- `position: sticky; top: 0; z-index: 100;`
- 内容(从左到右):
  - Logo + 项目名(TMLibrary)
  - 主导航链接:**首页 / 功能介绍 / 使用方法 / 联系方式 / 学习经历**
  - 「学习经历」:纯前端占位模块,**内部为空 + 提示文字**「开发此项目时的学习笔记,内容待添加」
  - 鼠标悬停带子模块的导航项时,**下拉显示子模块列表**,点击跳转
  - 右侧:明/暗模式 toggle(checkbox 样式)
- 移动端:汉堡菜单折叠展开
- 滚动时背景加 `backdrop-filter: blur(8px)` 实现毛玻璃

### 3.2 Hero 区

- **大标题**(英文,staggered 字母入场动画)
  - 桌面端:56px
  - 移动端:36px(**响应式缩放**)
  - 颜色:accent `#4CAF50`
  - 字体:Fraunces
- 标题下方一行副标题(中文,浅灰)
- 标题下面:
  - ~~**搜索框**:placeholder「请输入关键词」~~(v2 §7-#9 移除)
  - 「进入系统」主按钮(跳 `/login`),样式:accent 背景、白字、hover 提亮
- **动画**:字母从下方 20px 处淡入(`opacity 0 → 1`、`translateY 20px → 0`),每个字母 `animation-delay` 间隔 50ms
- 退出动画:**标题静止**(不参与页面退场动画,避免视觉混乱)

### 3.3 功能介绍区

- 背景:浅色模式 `#F5F5F5`,深色模式 `#1A1A1A`
- 标题 + 副标题居中
- **卡片网格**:
  - 桌面(≥1024px):3 列
  - 平板(768-1023px):2 列
  - 手机(<768px):1 列
- 每张卡片:
  - 图标(用 Element Plus icon 或 SVG)
  - 标题
  - 一句话简介
  - hover:`translateY(-4px)` + 阴影加深(过渡 200ms)

### 3.4 页脚

- 背景:`#212121`,文字:`#F5F5F5`
- 字号 14px,左对齐
- 三列:
  - 版权信息(© 2026 TMLibrary. All rights reserved.)
  - 联系方式(邮箱、GitHub 占位)
  - 备案号(占位文字)

> v2 §7-#10:页脚颜色用 token(`--color-footer-bg` / `--color-footer-text`)而非写死。

### 3.5 加载动画(首屏)

- 全屏覆盖层,背景 `#4CAF50`
- 中心显示项目名 + 进度条(纯 CSS 动画,无第三方库)
- 应用 ready 后淡出(200ms ease-out)

> v2 §7-#2:每次 session 只放一次,用 `sessionStorage` 标记。

### 3.6 返回顶部按钮

- 初始隐藏
- 滚动距离 > 1 个视口高度后显示(右下角,fixed)
- 样式:圆形、半透明白底 + accent 边框
- 点击:平滑滚动到顶部(`scrollTo({ top: 0, behavior: 'smooth' })`)

---

## 4. 交互 / 状态

### 4.1 主题系统

- 用 CSS 变量 + `:root[data-theme="dark"]` 切换
- toggle 按钮存选择到 `localStorage`(key: `tm_landing_theme`)
- 首次访问读 localStorage,无则跟随系统 `prefers-color-scheme`

> v2 §7-#1:状态从 `LandingNav` 局部 ref 抽到 `composables/useTheme.ts`,跨页面持久。

### 4.2 响应式断点

| 断点 | 宽度 | 用途 |
|------|------|------|
| mobile | <768px | 单列、汉堡菜单、字号缩小 |
| tablet | 768-1023px | 2 列 |
| desktop | ≥1024px | 3 列、完整字号 |

---

## 5. 性能要求

- 关键资源 `<link rel="preload">`
- 字体 `font-display: swap`
- 图片 `loading="lazy"`
- **不**引入未使用的库
- Lighthouse Performance 目标 ≥ 90

---

## 6. 技术约束

- 在现有 Vue 3 + Vite + TypeScript 工程内实现
- **不**装新依赖(Element Plus + Vue Router 已够用)
- 公开页面新建 `views/landing/Landing.vue`
- 学习经历拆独立组件 `views/landing/LearningLog.vue`,留 TODO 注释
- CSS 用 `<style scoped>` + CSS 变量(避免污染全局)
- 复用 Element Plus 图标(auto-import 已配)
- 路由 `/` 必须 `meta.public: true`(否则守卫会踢到 `/login`)
- AdminLayout 父路径 `/` → 改成 `/admin`
- 任何硬编码 `/dashboard`、`/books` 等路径要相应改为 `/admin/dashboard`、`/admin/books`
- 检查所有 `router.push(...)` 调用,确保路径同步更新

---

## 7. v2 实施 backlog(2026-08-22)

按依赖顺序排列,从低风险到高风险。先 Phase 1(快赢),再 Phase 2(a11y / 一次性逻辑),再 Phase 3(主题体系重构 + Login)。

| # | 任务 | 涉及文件 | 风险 |
|---|---|---|---|
| 1 | **移除 Landing 搜索框** | `views/landing/Landing.vue` | 🟢 低 |
| 2 | **路由切换时同步 `document.title`** | `router/index.ts` | 🟢 低 |
| 3 | **Footer 改用主题 token** | `styles/theme.css`(新建) + `views/landing/Landing.vue` | 🟢 低 |
| 4 | **NotFound 美化** | `views/NotFound.vue` | 🟢 低 |
| 5 | **Admin 侧栏高亮算法审计 + 注释** | `components/SidebarMenu.vue` | 🟢 低 |
| 6 | **添加 skip-link** | `views/landing/Landing.vue` + `styles/theme.css` | 🟡 中 |
| 7 | **`prefers-reduced-motion` 处理** | `styles/theme.css`(新建) | 🟡 中 |
| 8 | **LoadingScreen 一次性播放** | `views/landing/LoadingScreen.vue` | 🟡 中 |
| 9 | **style.css 清理深色背景写死** | `src/style.css` | 🟡 中 |
| 10 | **抽出 `theme.css` 替代 Landing 内联 token** | `src/styles/theme.css`(新建) + `main.ts` + `views/landing/Landing.vue` | 🟠 高 |
| 11 | **引入 `useTheme` composable** | `src/composables/useTheme.ts`(新建) + `views/landing/LandingNav.vue` + `layouts/AdminLayout.vue` | 🟠 高 |
| 12 | **Login 页统一主题** | `views/auth/Login.vue` | 🟠 高 |

### 7.1 当前状态 → 目标状态

#### #1 移除 Landing 搜索框(原 #9)
- **当前**:`hero-actions` 里有一个 `.search-box` div(⌕ 图标 + `<input>`),纯展示,没绑事件。
- **目标**:删除 `.search-box` 整块 DOM + 三个 CSS 类(`.search-box` / `.search-icon` / `.search-input`)。hero-actions 只保留 CTA 按钮。
- **验收**:`/favicon` 看不到搜索框;grep `search-box` 无结果。

#### #2 路由切换同步 `document.title`
- **当前**:浏览器标签页永远是「TMLibrary — 图书管理系统」。
- **目标**:`router.afterEach((to) => { document.title = (to.meta.title ? \`${to.meta.title} · TMLibrary\` : 'TMLibrary — 图书管理系统') })`。
- **验收**:跳 `/login` → 标签页变「登录 · TMLibrary」;跳 `/admin/dashboard` → 「仪表盘 · TMLibrary」;404 → 「页面不存在 · TMLibrary」。

#### #3 Footer 改用主题 token
- **当前**:`.landing-footer { background: #212121 }` 写死,暗色模式 `#0d0d0d`。
- **目标**:在 `theme.css` 加 `--color-footer-bg` 和 `--color-footer-text`,footer 引用。
- **验收**:grep `landing-footer.*#` 无结果(只引用变量)。

#### #4 NotFound 美化
- **当前**:裸 `<h1>404</h1>` + 按钮。
- **目标**:editorial 风格——大号 404(Fraunces 96px,accent 色)+ 一句俏皮文案 + 回首页 CTA,整体走主题 token。
- **验收**:NotFound 页面与 Landing 视觉一致(字体、配色、按钮风格)。

#### #5 Admin 侧栏高亮算法审计
- **当前**:最长前缀匹配,看起来对。
- **目标**:加注释解释算法(为什么 `idx + '/'` 前缀防误匹配),并在文件顶部加 mock 用例注释,覆盖 `/admin/books/123/edit`、`/admin/users`、`/admin/purchases/new` 等场景。
- **验收**:读代码就能推理出每个路径的高亮项,无需跑测试。

#### #6 添加 skip-link
- **当前**:无。
- **目标**:`<LandingNav>` 之前加 `<a href="#landing-main" class="skip-link">跳过导航,直达主内容</a>`;`<main class="landing">` 加 `id="landing-main"`。CSS 默认 `position: absolute; clip: rect(0,0,0,0)` 隐藏,`:focus` 时显示。
- **验收**:Tab 第一次按就能跳到 skip-link;Enter 后焦点到主内容区域。

#### #7 `prefers-reduced-motion`
- **当前**:Hero 字母动画、LoadingScreen pulse、deco-circle 浮动不响应系统设置。
- **目标**:`theme.css` 加 `@media (prefers-reduced-motion: reduce)` 全局规则:`*, *::before, *::after { animation-duration: 0.01ms !important; animation-iteration-count: 1 !important; transition-duration: 0.01ms !important; scroll-behavior: auto !important; }`。
- **验收**:macOS 设置「减弱动态效果」后,Landing 字母直接出现,不动画。

#### #8 LoadingScreen 一次性
- **当前**:每次进 `/` 都重播 2 秒。
- **目标**:`onMounted` 检查 `sessionStorage.getItem('tm_landing_loaded')`,有则 `visible.value = false`;否则播完设 `'tm_landing_loaded' = '1'`。
- **验收**:第一次进 / 看到 LoadingScreen;刷新或离开后回来立即隐藏。

#### #9 style.css 清理
- **当前**:`src/style.css` 的 `:root { background-color: #242424 }` 是 Vite 模板默认值,跟 Landing 的 `body { background: var(--color-bg) }` 抢。
- **目标**:删掉 `background-color` 和 `color: rgba(255,255,255,0.87)`,只保留 `box-sizing`、`min-width/height`、`color-scheme`。
- **验收**:`grep background-color src/style.css` 无结果。

#### #10 抽出 `theme.css`
- **当前**:`Landing.vue` 里有一段非 scoped `<style>` 写 `:root` 变量,逻辑上是全局样式但住在组件里。
- **目标**:新建 `src/styles/theme.css`,搬 :root 变量 + body 字体 + html scroll-behavior + skip-link + reduced-motion。`main.ts` 在 element-plus 之后引入。`Landing.vue` 删除对应 `<style>` 块。
- **依赖**:#3 #6 #7 在此之前完成(它们的 token 都依赖 theme.css)。
- **验收**:`grep ":root" src/views/landing/Landing.vue` 无结果。

#### #11 引入 `useTheme` composable
- **当前**:`LandingNav.vue` 的 `theme` 是局部 `ref`,只在自己挂载时跑 `syncTheme()`。切到 admin 再回来会丢。
- **目标**:新建 `src/composables/useTheme.ts`,模块级单例 ref + `initTheme()`(localStorage > prefers-color-scheme) + `toggleTheme()` + `setTheme(t)`。`LandingNav` 调用 `initTheme()` onMounted。`AdminLayout` 也调用一次保证一致性。
- **验收**:进 /admin/dashboard → 切回 /,主题不闪;切换主题后立即刷新,选择保留。

#### #12 Login 改主题
- **当前**:`background: linear-gradient(135deg, #1f2937, #0f172a)` 深蓝紫,跟 Landing 暖米白/绿完全脱节。
- **目标**:用 `var(--color-bg)` 做底 + 居中卡片 + `var(--color-card)` 卡片背景 + accent 按钮;卡片保留 shadow,但颜色全走 token。
- **验收**:Login 页面与 Landing 视觉对齐(同一字体、同一 accent 色、同一卡片风格)。

---

## 8. 验收命令

```bash
cd frontend
npm run build          # vue-tsc 类型检查 + vite build
npm run dev            # 手测
```

每次提交前跑一次 `npm run build`,确保不引入类型错误。