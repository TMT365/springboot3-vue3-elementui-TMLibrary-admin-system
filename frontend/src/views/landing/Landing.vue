<script setup lang="ts">
/**
 * Landing  -  公开欢迎页(/ 路由)
 *
 * 设计方向:editorial / 书卷气  -  配合 TMLibrary 主题
 * - 所有 display 元素用 DM Serif Display(serif),正文 Manrope(sans)
 * - 绿色 #4CAF50 仅作 accent,不铺满
 * - 暖米白底(#FAF8F3)+ 深炭灰暗色模式(#1A1A1A)
 *
 * Sections:
 * 1. Nav(由 LandingNav 接管)
 * 2. Hero  -  大标题 + 搜索框 + 进入系统 CTA
 * 3. Showcase  -  应用展示轮播
 * 4. Features  -  3 张卡片(图书/订单/用户)
 * 5. Learning Log  -  学习经历摘要(全文在 /journey)
 * 6. Project Structure  -  仓库入口 + 目录树 + 学习路线 + AI Agent 规划
 * 7. Join Us  -  邀请贡献者
 * 8. Contact  -  联系方式
 * 9. Footer  -  版权 + 联系方式 + 备案号
 *
 * Overlays:
 * - BackToTop  -  滚动后右下角(带阅读进度环,叠在 FeedbackFab 上方)
 *
 * 注:加载动画(LoadingScreen)已提到 App.vue 全局,所有路由刷新都会播,
 *     本页不再自己挂载。
 */
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import LandingNav from './LandingNav.vue'
import BackToTop from './BackToTop.vue'
import SpecialThanks from './SpecialThanks.vue'
// Hero 右下角的终端演示窗(绝对定位,窄屏回到文档流)
import HeroTerminal from './HeroTerminal.vue'
// 学习经历的数据源 —— 首页只展示三张分类摘要卡,全文在 /journey
import { CATEGORY_META, JOURNEY_ENTRIES } from '@/views/journey/entries'
import type { JourneyCategory } from '@/views/journey/entries'

const router = useRouter()

/** 三类经历的条数 —— 从真实数据算,不写死(加一条经历首页自动跟着变) */
const journeyStats = computed(() =>
  (Object.keys(CATEGORY_META) as JourneyCategory[]).map((key) => ({
    key,
    ...CATEGORY_META[key],
    count: JOURNEY_ENTRIES.filter((e) => e.category === key).length,
  })),
)

const heroTitle = 'Manage Your Library Beautifully'

const features = [
  {
    id: 'feature-books',
    icon: 'Reading',
    title: '图书管理',
    desc: '增删改查图书信息,支持 ISBN、价格、库存、出版日期等多维度搜索。',
  },
  {
    id: 'feature-orders',
    icon: 'List',
    title: '订单管理',
    desc: '一键下单,实时追踪订单状态。下单时自动锁定库存,价格快照留痕。',
  },
  {
    id: 'feature-users',
    icon: 'UserFilled',
    title: '用户管理',
    desc: '角色权限分明(USER / ADMIN / BOSS),登录失败锁定、密码修改、账号启停一站搞定。',
  },
]

const currentYear = new Date().getFullYear()

/* =============================================================
 * 项目结构区块(2026-09 加)
 *
 * 面向"想读代码 / 想参与"的人。内容全部可核对 —— 目录树照着仓库实际
 * 层级写,GitHub 链接是真实 remote,学习路线是本项目真实走过的阶段。
 * ============================================================= */

/** 真实仓库地址,取自 git remote origin —— 所有出站链接都由它拼,改一处即可 */
const GITHUB_URL = 'https://github.com/TMT365/springboot3-vue3-elementui-TMLibrary-admin-system'

/**
 * README 的直达链接。
 * <p>仓库根目录没有 README(只有前端工程里有一个),所以不能链 `#readme` 锚点 ——
 * 那会落在一个空白页上。这里指到真实存在的那份文件。</p>
 */
const README_URL = `${GITHUB_URL}/blob/main/frontend/README.md`

/** 仓库属主的 GitHub 主页 —— 从仓库地址里切出来,避免两处各写一遍再写歪 */
const GITHUB_PROFILE_URL = 'https://github.com/TMT365'

/**
 * 项目贡献者。
 *
 * <p><b>为什么是 Contributors 而不是 Sponsors</b>:原来的标题是「项目 Sponsors」、
 * 注释写着要接 GitHub Sponsors API —— 但那个 API 要求账号已经开通赞助功能,
 * 而且"赞助"是给钱,"贡献"是给代码/文档,两件事。对这个项目来说
 * 「贡献者」才是真实的那个概念,对应的
 * <code>/repos/{owner}/{repo}/contributors</code> 是公开接口、不需要鉴权,
 * 接进来就能用。</p>
 *
 * <p>拉回数据后直接填进这个数组即可,模板不用动。</p>
 */
interface Contributor {
  id: string
  name: string
  avatar: string
  url: string
}

/** 目前还没有真实数据 —— 故意留空,让空态如实显示,而不是拿 6 个假头像充数 */
const contributors: Contributor[] = []

/** 空态下铺几个槽位 —— 视觉上说明"这里是放人的地方" */
const CONTRIBUTOR_SLOTS = 6

/**
 * 联系方式。
 *
 * <h2>为什么是"卡片 = 容器 + 值 = 链接"</h2>
 * <p>原来整张卡就是一个 &lt;a&gt;,前提是"一张卡只有一个值"。邮箱变成两个之后
 * 这个前提没了 —— 一张卡指不了两个 mailto。所以结构改成:</p>
 * <ul>
 *   <li><b>卡片</b>只是个容器(div),负责色相和排版</li>
 *   <li><b>每个值</b>自己才是 &lt;a&gt;,自己带下划线扫过</li>
 *   <li>卡片整体的抬起只给"里面确实有链接"的卡(link 标记)——
 *       纯展示的卡不该装得像能点</li>
 * </ul>
 *
 * <p>注意 tangmingtao111.@gmail.com 的 @ 前面那个点是按原样写的,
 * 没有自作主张删掉 —— 但那不是合法的邮箱本地部分,见下方注释。</p>
 */
interface ContactValue {
  text: string
  /** 有 href 才渲染成可点的 <a> */
  href?: string
}

interface ContactEntry {
  /** 同时也是色相类名后缀:is-mail / is-github / is-project */
  key: string
  icon: string
  label: string
  values: ContactValue[]
  /** 卡内有链接 → hover 时整卡抬起 */
  link?: boolean
}

const contactEntries: ContactEntry[] = [
  {
    key: 'mail',
    icon: 'Message',
    label: '邮箱',
    link: true,
    values: [
      { text: '98206858@qq.com', href: 'mailto:98206858@qq.com' },
      // ⚠ @ 前面这个点是原样保留的。RFC 5321 的 dot-atom 不允许以点结尾,
      //   所以这不是一个合法地址,Gmail 也不会认 —— 大概率是手误,
      //   但那是你的数据,我不擅自改。要改成 tangmingtao111@gmail.com 说一声。
      { text: 'tangmingtao111.@gmail.com', href: 'mailto:tangmingtao111.@gmail.com' },
    ],
  },
  {
    key: 'github',
    icon: 'Link',
    label: 'GitHub',
    link: true,
    values: [{ text: '@TMT365', href: GITHUB_PROFILE_URL }],
  },
  {
    key: 'project',
    icon: 'Collection',
    label: '项目',
    // 没有 href → 不是链接 → 不抬起、不下划线
    values: [{ text: 'springboot3-vue3-elementui' }],
  },
]

/** 三个入口:仓库 / README / 学习经历全文 */
const psEntries = [
  {
    key: 'repo',
    icon: 'Share',
    label: 'GitHub 仓库',
    value: 'TMT365 / springboot3-vue3-elementui-TMLibrary-admin-system',
    go: '打开仓库',
    href: GITHUB_URL,
  },
  {
    key: 'readme',
    icon: 'Document',
    label: 'README',
    value: '前端目录说明 / 架构约定 / 常见任务',
    go: '在线阅读',
    href: README_URL,
  },
] as const

/**
 * 加入我们 —— 三类"可能合适"的人。
 *
 * <p>文案刻意收着写:陈述事实而不是号召,承认对方不一定需要,
 * 不加感叹号、不写"还等什么"。理由见区块样式上方的注释。</p>
 */
const joinRoles = [
  {
    no: '01',
    title: '带着想法来',
    desc: '你手上有个还没落地的设想 —— 一个功能、一处改法、一种更顺的结构。这里可以把它写成能跑的代码,交给真实用户去用。好不好用,试过才知道。',
  },
  {
    no: '02',
    title: '想读懂一个完整项目',
    desc: '从目录怎么分层,到每个取舍的来龙去脉,这里都留了痕迹。不必从头读,挑一个感兴趣的模块钻进去就行,卡住了直接问。',
  },
  {
    no: '03',
    title: '想留下点痕迹',
    desc: '从修一处小 Bug、补一段文档开始都算数。做过的事会留在提交记录里,时间长了自然看得见。',
  },
]

/**
 * 目录树 —— 直接对应仓库真实层级,不是示意图。
 *
 * <p>拆成结构化行(而不是一整个多行字符串)是为了能给三个部分分别上色:
 * 树形前缀压暗、目录名正常、注释更淡。原来整块一个 --color-text-muted,
 * 20 行全一个灰度,扫起来是一坨。</p>
 *
 * <p><b>branch 里的空格是结构的一部分</b> —— 竖线 │ 靠它们对齐,
 * 不要格式化掉。</p>
 */
interface TreeRow {
  /** 树形前缀(├── │ 等),等宽字体下靠前导空格对齐 */
  branch: string
  name: string
  note?: string
  /** 顶层节点:亮一档 + 加粗 */
  root?: boolean
}

const PS_TREE: TreeRow[] = [
  { branch: '', name: 'springboot3-vue3-elementui-TMLibrary-admin-system/', note: '仓库根', root: true },

  { branch: '├── ', name: 'TMLibrary/', note: 'Spring Boot 4 后端' },
  { branch: '│   └── ', name: 'src/main/java/com/tmt/TMLibrary/' },
  { branch: '│       ├── ', name: 'controller/', note: 'REST 入口(参数校验 + 权限判定)' },
  { branch: '│       ├── ', name: 'service/', note: '业务逻辑' },
  { branch: '│       │   ├── ', name: 'impl/', note: '各领域实现' },
  { branch: '│       │   └── ', name: 'search/', note: 'ES 候选词 + 缓存装饰器' },
  { branch: '│       ├── ', name: 'mapper/', note: 'MyBatis 接口(手写配置,不用 starter)' },
  { branch: '│       ├── ', name: 'entity/ dto/ vo/', note: '三层数据模型,互不串用' },
  { branch: '│       ├── ', name: 'security/', note: 'JWT 过滤器 · IP 风控 · 当前用户解析' },
  { branch: '│       ├── ', name: 'scheduler/', note: '订单超时关单 · 库存对账' },
  { branch: '│       └── ', name: 'common/', note: '统一返回体 · Redis Key · 枚举' },

  { branch: '└── ', name: 'frontend/', note: 'Vue 3 + TS 前端' },
  { branch: '    └── ', name: 'src/' },
  { branch: '        ├── ', name: 'api/', note: '按领域封装的请求函数' },
  { branch: '        ├── ', name: 'views/', note: '页面(book / mall / purchase / user / feedback / journey)' },
  { branch: '        ├── ', name: 'layouts/', note: '三套外壳:Admin / Mall / User' },
  { branch: '        ├── ', name: 'components/', note: '可复用组件(Pager / FormDialog / FeedbackFab)' },
  { branch: '        ├── ', name: 'composables/', note: 'useTheme · useFeedback · useBookSuggest' },
  { branch: '        └── ', name: 'utils/request.ts', note: '唯一的 HTTP 封装(拆 Result + 401 处理)' },
]

/** 学习路线 —— 本项目真实走过的五个阶段,前四个已落地 */
const psRoadmap = [
  {
    stage: '01',
    title: '把 CRUD 做扎实',
    desc: 'Spring Boot 分层 + MyBatis 手写配置 + JWT 鉴权,先把"能跑通"变成"分层清楚"。',
    done: true,
  },
  {
    stage: '02',
    title: '和数据一致性打交道',
    desc: 'Redis 缓存、库存扣减、订单超时关单 —— 第一次认真处理并发和"缓存和库不一致"。',
    done: true,
  },
  {
    stage: '03',
    title: '中文搜索',
    desc: 'Elasticsearch + IK 分词器,顺带补上 Redis 缓存装饰器:先查缓存,没有再问 ES。',
    done: true,
  },
  {
    stage: '04',
    title: '可观测性',
    desc: '企业级日志:traceId 贯穿一次请求,框架日志降噪,ERROR 单独落文件。',
    done: true,
  },
  {
    stage: '05',
    title: '往 AI Agent 走',
    desc: '下一步 —— 让系统从"被动查询"变成"主动办事",见下方三个方向。',
    done: false,
  },
]

/** 后续规划:AI Agent 方向 —— 都挂在本项目已有的能力上,不是空想 */
const psNext = [
  {
    icon: 'MagicStick',
    title: '图书语义检索 Agent',
    desc: '现在搜索是"关键词匹配",用户得先想对词。接入 Agent 后可以问「有没有讲分布式事务、写得不太厚、适合入门的中文书」—— 由 Agent 拆解成意图条件,再决定查 ES 还是查库,多轮澄清。',
    tag: '接搜索',
  },
  {
    icon: 'ChatLineSquare',
    title: '工单处理 Agent',
    desc: '接刚上线的反馈系统:新工单进来先自动归类、检索历史上相似的已解决工单,给管理员起草一版回复。人只做审核和发送,不做从零写起。',
    tag: '接反馈',
  },
  {
    icon: 'DataAnalysis',
    title: '运营问答 Agent',
    desc: '接仪表盘统计:用自然语言问「上个月哪三类书卖得最好」,Agent 生成查询、跑数据、出图。把写 SQL 的门槛从"会写"降到"会问"。',
    tag: '接统计',
  },
]

/**
 * Showcase 轮播  -  占位色块版
 * 后续替换为真实图片 + 文案
 * 4 张 slide 渐变色,5s 自动切换,圆点可手动点
 *
 * 调色板:书脊 / 藏书主题  -  森林 / 琥珀 / 橄榄 / 墨青,避开紫粉青等 AI stock 渐变。
 */
const currentShowcaseSlide = ref(0)
const showcaseItems = [
  {
    id: 'showcase-1',
    color: 'linear-gradient(135deg, #2e5e3e 0%, #4caf50 100%)',
    title: '封面展示 · 01',
    desc: '即将填充实际产品截图',
  },
  {
    id: 'showcase-2',
    color: 'linear-gradient(135deg, #ffc107 0%, #ff9800 100%)',
    title: '管理界面 · 02',
    desc: '即将填充实际产品截图',
  },
  {
    id: 'showcase-3',
    color: 'linear-gradient(135deg, #556b2f 0%, #8fbc8f 100%)',
    title: '数据看板 · 03',
    desc: '即将填充实际产品截图',
  },
  {
    id: 'showcase-4',
    color: 'linear-gradient(135deg, #1f3a3d 0%, #5c8a6f 100%)',
    title: '移动端 · 04',
    desc: '即将填充实际产品截图',
  },
]

let showcaseTimer: ReturnType<typeof setInterval> | null = null

function startShowcase(): void {
  if (showcaseTimer) clearInterval(showcaseTimer)
  showcaseTimer = setInterval(() => {
    currentShowcaseSlide.value = (currentShowcaseSlide.value + 1) % showcaseItems.length
  }, 5000)
}

function pauseShowcase(): void {
  if (showcaseTimer) {
    clearInterval(showcaseTimer)
    showcaseTimer = null
  }
}

function resumeShowcase(): void {
  startShowcase()
}

onMounted(startShowcase)
onUnmounted(pauseShowcase)
</script>

<template>
  <a href="#landing-main" class="skip-link">跳过导航,直达主内容</a>

  <LandingNav />

  <main id="landing-main" class="landing">
    <!-- ============== Hero ============== -->
    <section id="home" class="hero">
      <div class="hero-inner">
        <div class="hero-eyebrow">
          Spring Boot · Vue 3 · Element Plus
        </div>

        <h1 class="hero-title">{{ heroTitle }}</h1>

        <p class="hero-subtitle">
          一个用现代全栈技术搭建的图书管理系统。
          <br />
          从书架到订单,让图书馆的每一次呼吸都被记录。
        </p>

        <div class="hero-actions">
          <router-link to="/mall" class="cta-primary">
            进入商城
          </router-link>
        </div>
      </div>

      <div class="hero-deco" aria-hidden="true">
        <div class="deco-circle deco-circle-1" />
        <div class="deco-circle deco-circle-2" />
      </div>

      <!-- 怎么把它跑起来 —— 四步循环演示。桌面端绝对定位在右下角,
           窄屏回到文档流跟在 CTA 下面(见 .hero-terminal 的媒体查询) -->
      <div class="hero-terminal">
        <HeroTerminal />
      </div>
    </section>

    <!-- ============== Showcase  -  占位色块轮播(后续换图) ============== -->
    <section id="showcase" class="showcase" v-reveal>
      <header class="section-head">
        <el-tag class="section-tag" size="small" effect="plain" type="success">Showcase</el-tag>
        <h2 class="section-title">应用展示</h2>
        <p class="section-sub">即将到来 · 当前是占位色块</p>
      </header>

      <div class="showcase-stage">
        <div
          class="showcase-track"
          @mouseenter="pauseShowcase"
          @mouseleave="resumeShowcase"
        >
          <div
            v-for="(slide, i) in showcaseItems"
            :key="slide.id"
            class="showcase-slide"
            :class="{ active: i === currentShowcaseSlide }"
            :style="{ background: slide.color }"
            :aria-hidden="i !== currentShowcaseSlide"
            role="group"
            :aria-label="`Slide ${i + 1} of ${showcaseItems.length}`"
          >
            <div class="showcase-slide-content">
              <span class="showcase-slide-tag">Slide {{ String(i + 1).padStart(2, '0') }}</span>
              <h3 class="showcase-slide-title">{{ slide.title }}</h3>
              <p class="showcase-slide-desc">{{ slide.desc }}</p>
            </div>
          </div>
        </div>

        <div class="showcase-dots" role="tablist">
          <button
            v-for="(slide, i) in showcaseItems"
            :key="slide.id"
            class="showcase-dot"
            :class="{ active: i === currentShowcaseSlide }"
            :aria-label="`跳到第 ${i + 1} 张`"
            :aria-selected="i === currentShowcaseSlide"
            role="tab"
            @click="currentShowcaseSlide = i"
          ></button>
        </div>
      </div>
    </section>

    <!-- ============== Features ============== -->
    <section class="features" v-reveal>
      <header class="section-head">
        <h2 class="section-title">核心功能一览</h2>
        <p class="section-sub">一个后台,管好一座图书馆</p>
      </header>

      <div class="feature-list">
        <article
          v-for="(f, i) in features"
          :id="f.id"
          :key="f.id"
          class="feature-row"
          :class="{ reverse: i % 2 === 1 }"
          v-reveal="{ delay: i * 120 }"
        >
          <div class="feature-text-col">
            <div class="feature-icon-wrap">
              <el-icon :size="28"><component :is="f.icon" /></el-icon>
            </div>
            <h3 class="feature-title">{{ f.title }}</h3>
            <p class="feature-desc">{{ f.desc }}</p>
          </div>

          <div class="feature-visual-col">
            <div class="feature-visual-stage" aria-hidden="true">
              <div class="visual-layer layer-1">
                <div class="skeleton-img" role="presentation" />
              </div>
              <div class="visual-layer layer-2">
                <div class="skeleton-img" role="presentation" />
              </div>
              <div class="visual-layer layer-3">
                <div class="skeleton-img" role="presentation" />
              </div>
            </div>
          </div>
        </article>
      </div>
    </section>

    <!-- ============== Learning Log ============== -->
    <section id="learning-log" class="learning-log" v-reveal>
      <header class="section-head">
        <h2 class="section-title">学习经历</h2>
        <p class="section-sub">开发此项目时遇到的问题、踩过的坑、学到的知识点</p>
      </header>

      <!-- 三张分类卡 —— 数量从真实数据里算,不写死。
           点任意一张(或底部按钮)进 /journey 看全文 -->
      <div class="learning-grid">
        <article
          v-for="cat in journeyStats"
          :key="cat.key"
          class="learning-card is-clickable"
          :style="{ '--c': cat.color }"
          role="button"
          tabindex="0"
          @click="router.push('/journey')"
          @keyup.enter="router.push('/journey')"
        >
          <span class="learning-card-icon" aria-hidden="true">
            <el-icon><component :is="cat.icon" /></el-icon>
          </span>
          <span class="learning-card-tag">{{ cat.label }}</span>
          <h3 class="learning-card-title">
            {{ cat.count }} 条真实经历
          </h3>
          <p class="learning-card-body">{{ cat.desc }}</p>
          <span class="learning-card-status is-ready">
            查看详情
            <el-icon><ArrowRight /></el-icon>
          </span>
        </article>
      </div>

      <p class="learning-note">
        全部是开发这个项目时真实踩过的坑 ——
        每条都写了"症状 / 根因 / 解法 / 收获",
        <button class="learning-link" type="button" @click="router.push('/journey')">
          查看完整学习经历
          <el-icon><ArrowRight /></el-icon>
        </button>
      </p>
    </section>

    <!-- ============== Project Structure ============== -->
    <section id="project-structure" class="ps" v-reveal>
      <header class="section-head">
        <h2 class="section-title">项目结构</h2>
        <p class="section-sub">代码在哪、怎么读、接下来往哪走</p>
      </header>

      <!-- 三个入口 —— 前两个出站(GitHub),第三个站内跳学习经历全文 -->
      <div class="ps-entries">
        <a
          v-for="entry in psEntries"
          :key="entry.key"
          class="ps-entry"
          :href="entry.href"
          target="_blank"
          rel="noopener noreferrer"
        >
          <span class="ps-entry-icon" aria-hidden="true">
            <el-icon><component :is="entry.icon" /></el-icon>
          </span>
          <span class="ps-entry-label">{{ entry.label }}</span>
          <span class="ps-entry-value">{{ entry.value }}</span>
          <span class="ps-entry-go">
            {{ entry.go }}
            <el-icon><ArrowRight /></el-icon>
          </span>
        </a>

        <button class="ps-entry" type="button" @click="router.push('/journey')">
          <span class="ps-entry-icon" aria-hidden="true">
            <el-icon><Notebook /></el-icon>
          </span>
          <span class="ps-entry-label">学习经历</span>
          <span class="ps-entry-value">
            {{ JOURNEY_ENTRIES.length }} 条踩坑笔记,按「症状 / 根因 / 解法 / 收获」拆开写
          </span>
          <span class="ps-entry-go">
            进入阅读
            <el-icon><ArrowRight /></el-icon>
          </span>
        </button>
      </div>

      <!-- 目录树 —— 让"项目结构"回到它字面上的意思 -->
      <div class="ps-tree-block">
        <h3 class="ps-h3">目录结构</h3>
        <p class="ps-h3-sub">前后端两个工程,各自分层;先看目录,再挑一个包钻进去</p>
        <!-- 每行三部分:树形前缀 / 目录名 / 注释,分别上色。
             窄屏交给父级横向滚动,不折行 —— 折了树就断了 -->
        <div class="ps-tree">
          <div
            v-for="(row, i) in PS_TREE"
            :key="i"
            class="ps-row"
            :class="{ 'is-root': row.root }"
          >
            <span class="ps-branch">{{ row.branch }}</span>
            <span class="ps-name">{{ row.name }}</span>
            <span v-if="row.note" class="ps-note">{{ row.note }}</span>
          </div>
        </div>
      </div>

      <!-- 学习路线 -->
      <div class="ps-roadmap-block">
        <h3 class="ps-h3">学习路线</h3>
        <p class="ps-h3-sub">这个项目实际走过的五步 —— 每一步都是被问题推着走的</p>
        <ol class="ps-roadmap">
          <li
            v-for="step in psRoadmap"
            :key="step.stage"
            class="ps-step"
            :class="{ 'is-next': !step.done }"
          >
            <span class="ps-step-num">{{ step.stage }}</span>
            <div class="ps-step-body">
              <h4 class="ps-step-title">
                {{ step.title }}
                <span v-if="step.done" class="ps-step-badge">
                  <el-icon><CircleCheckFilled /></el-icon>
                  已落地
                </span>
                <span v-else class="ps-step-badge is-next">进行中</span>
              </h4>
              <p class="ps-step-desc">{{ step.desc }}</p>
            </div>
          </li>
        </ol>
      </div>

      <!-- 下一步:AI Agent -->
      <div class="ps-next-block">
        <h3 class="ps-h3">接下来:往 AI Agent 方向走</h3>
        <p class="ps-h3-sub">
          不是"接个模型聊天",而是让系统从「你问它答」变成「它替你办」——
          三个方向都接在已经跑通的能力上
        </p>
        <div class="ps-next-grid">
          <article v-for="item in psNext" :key="item.title" class="ps-next-card">
            <span class="ps-next-icon" aria-hidden="true">
              <el-icon><component :is="item.icon" /></el-icon>
            </span>
            <h4 class="ps-next-title">{{ item.title }}</h4>
            <p class="ps-next-desc">{{ item.desc }}</p>
            <span class="ps-next-tag">{{ item.tag }}</span>
          </article>
        </div>
      </div>
    </section>

    <!-- ============== Join Us ============== -->
    <section id="join-us" class="join-us" v-reveal>
      <header class="section-head">
        <h2 class="section-title">加入我们</h2>
        <p class="section-sub">
          如果这个项目恰好和你在做的事有重叠,欢迎一起。不必是完整的方案 ——
          一个 Issue、一处文档修正,都是好的开始。
        </p>
      </header>

      <ol class="join-list">
        <li v-for="role in joinRoles" :key="role.no" class="join-item">
          <!-- 序号是纯装饰:ol 本身已经带序号语义,读屏不必再念一遍 -->
          <span class="join-num" aria-hidden="true">{{ role.no }}</span>
          <h3 class="join-item-title">{{ role.title }}</h3>
          <p class="join-item-desc">{{ role.desc }}</p>
        </li>
      </ol>

      <div class="join-cta">
        <p class="join-cta-text">
          项目一直在改,也一直缺人手。<br />
          如果上面有哪一条让你觉得「这说的好像是我」,不妨先来看看代码。
        </p>

        <!-- 一主一辅两个入口 —— 辅按钮的存在本身就是"委婉":
             还没打算动手的人,也有一条更轻的路可以走 -->
        <div class="join-cta-actions">
          <a
            :href="GITHUB_URL"
            target="_blank"
            rel="noopener noreferrer"
            class="join-cta-btn"
          >
            去 GitHub 看看
            <el-icon class="cta-arrow"><ArrowRight /></el-icon>
          </a>
          <a
            :href="README_URL"
            target="_blank"
            rel="noopener noreferrer"
            class="join-cta-btn is-ghost"
          >
            先读 README
          </a>
        </div>
      </div>

      <!-- 项目贡献者 —— 空态如实显示,不拿假头像充数 -->
      <div class="contributors">
        <h3 class="contributors-title">项目贡献者</h3>
        <p class="contributors-subtitle">
          <template v-if="contributors.length">这个项目不是一个人写完的</template>
          <template v-else>这里目前还空着 —— 第一个位置留给你</template>
        </p>

        <ul class="contributors-grid" aria-label="项目贡献者">
          <!-- 真实数据分支:接上 GitHub Contributors API 后走这条 -->
          <li
            v-for="(person, i) in contributors"
            :key="person.id"
            class="contributor"
            :style="{ '--i': i }"
          >
            <a :href="person.url" target="_blank" rel="noopener noreferrer" class="contributor-link">
              <img
                :src="person.avatar"
                :alt="person.name"
                class="contributor-avatar"
                loading="lazy"
                decoding="async"
              />
              <span class="contributor-name">{{ person.name }}</span>
            </a>
          </li>

          <!-- 空态:骨架槽位。aria-hidden —— 没有内容可念 -->
          <template v-if="!contributors.length">
            <li
              v-for="i in CONTRIBUTOR_SLOTS"
              :key="`slot-${i}`"
              class="contributor is-placeholder"
              :style="{ '--i': i - 1 }"
              aria-hidden="true"
            >
              <span class="contributor-avatar skeleton-circle" />
              <span class="contributor-name-skeleton skeleton-bar" />
            </li>
          </template>
        </ul>
      </div>
    </section>

    <!-- ============== Contact ============== -->
    <section id="contact" class="contact" v-reveal>
      <header class="section-head">
        <h2 class="section-title">联系方式</h2>
      </header>

      <div class="contact-grid">
        <!-- 一张卡一个色相(is-mail / is-github / is-project)——
             见 theme.css §1.5 辅助色相:三张卡同色就没有区分度。
             卡片本身是 div,链接下沉到每个值上 —— 邮箱那张有两个地址,
             整卡包一个 <a> 就指不了两个 mailto 了 -->
        <div
          v-for="entry in contactEntries"
          :key="entry.key"
          class="contact-card"
          :class="[`is-${entry.key}`, { 'has-links': entry.link }]"
        >
          <div class="contact-head">
            <span class="contact-icon" aria-hidden="true">
              <el-icon><component :is="entry.icon" /></el-icon>
            </span>
            <span class="contact-label">{{ entry.label }}</span>
          </div>

          <div class="contact-values">
            <template v-for="(v, i) in entry.values" :key="i">
              <a
                v-if="v.href"
                class="contact-value"
                :href="v.href"
                :target="v.href.startsWith('mailto:') ? undefined : '_blank'"
                :rel="v.href.startsWith('mailto:') ? undefined : 'noopener noreferrer'"
              >{{ v.text }}</a>
              <span v-else class="contact-value">{{ v.text }}</span>
            </template>
          </div>
        </div>
      </div>
    </section>

    <!-- ============== Special Thanks  -  横向滚动鸣谢条 ============== -->
    <SpecialThanks />

    <!-- ============== Footer ============== -->
    <footer class="landing-footer">
      <div class="footer-inner">
        <div class="footer-col">
          <div class="footer-logo">TMLibrary</div>
          <p class="footer-tagline">Manage Your Library Beautifully.</p>
        </div>
        <div class="footer-col">
          <h4>联系</h4>
          <p>tmt@example.com</p>
          <p>
            GitHub:
            <a :href="GITHUB_PROFILE_URL" target="_blank" rel="noopener noreferrer">@TMT365</a>
          </p>
        </div>
        <div class="footer-col">
          <h4>备案</h4>
          <p>京 ICP 备 XXXXXXXX 号(占位)</p>
        </div>
      </div>
      <div class="footer-bottom">
        © {{ currentYear }} TMLibrary. All rights reserved.
      </div>
    </footer>
  </main>

  <BackToTop />
</template>

<style scoped>
/* =============================================================
 * Page layout
 * ============================================================= */
.landing {
  min-height: 100vh;
  background: var(--color-bg);
  color: var(--color-text);
  position: relative;
  overflow-x: hidden;
}

/* =============================================================
 * Skip-link  -  键盘 / 屏幕阅读器用户跳过 nav 直接到主内容
 * 默认 clip 隐藏,focus 时显示;不影响视觉用户
 * ============================================================= */
.skip-link {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 200;
  padding: 10px 18px;
  background: var(--color-accent);
  color: #fff;
  font-size: 14px;
  font-weight: 500;
  border-radius: 6px;
  text-decoration: none;
  clip: rect(0, 0, 0, 0);
  width: 1px;
  height: 1px;
  overflow: hidden;
  white-space: nowrap;
}

.skip-link:focus {
  clip: auto;
  width: auto;
  height: auto;
  overflow: visible;
  outline: 2px solid #fff;
  outline-offset: 2px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

/* =============================================================
 * Section heads(共用)
 * ============================================================= */
.section-head {
  text-align: center;
  margin-bottom: 48px;
}


.section-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(40px, 5vw, 56px);
  /* DM Serif Display 只发布 weight 400。组合下面三条出"粗"感:
     - 合成 bold(font-weight 700) -  浏览器给每个字形额外加一道轮廓
     - 字号 +18-40%  -  大本身就是视觉权重
     - letter-spacing -0.04em  -  字距收紧让字符更紧凑,体感更重
     (注:原先注释的结束标记写在了这三条声明下面,把它们一起吞了 ——
      标题实际只有 font-family + font-size 生效,比设计意图轻且没有下边距) */
  font-weight: 700;
  letter-spacing: -0.04em;
  margin: 0 0 8px;
}

.section-sub {
  font-size: 15px;
  color: var(--color-text-muted);
  margin: 0;
}

/* =============================================================
 * Hero
 * ============================================================= */
.hero {
  position: relative;
  /* 占满整个画面 — 100dvh 适配移动端浏览器地址栏收起/展开 */
  min-height: 100vh;
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 24px;
  text-align: center;
  overflow: hidden;

  /* 3D 主题背景 — 多层 radial + linear 叠加,营造光感与层次
     第 1 层:顶部光晕(accent 绿),模拟主光源从上方斜照
     第 2 层:底部暖光(emphasis 琥珀),平衡冷暖
     第 3 层:垂直渐变 bg → bg-alt,给底色加纵深 */
  background:
    radial-gradient(ellipse 90% 50% at 50% 0%, rgba(76, 175, 80, 0.10) 0%, transparent 60%),
    radial-gradient(ellipse 60% 40% at 50% 100%, rgba(255, 193, 7, 0.07) 0%, transparent 60%),
    linear-gradient(180deg, var(--color-bg) 0%, var(--color-bg) 50%, var(--color-bg-alt) 100%);
}

:root[data-theme='dark'] .hero {
  /* 暗模式:深底反衬,光感增强(高 1.5-2× 不透明度),色相更亮 */
  background:
    radial-gradient(ellipse 90% 50% at 50% 0%, rgba(102, 187, 106, 0.18) 0%, transparent 60%),
    radial-gradient(ellipse 60% 40% at 50% 100%, rgba(255, 213, 79, 0.10) 0%, transparent 60%),
    linear-gradient(180deg, var(--color-bg) 0%, var(--color-bg) 50%, var(--color-bg-alt) 100%);
}

.hero-inner {
  max-width: 100%;
  margin: 0 auto;
  position: relative;
  z-index: 2;
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  margin-bottom: 28px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 999px;
  font-size: 13px;
  color: var(--color-text-muted);
}

.hero-title {
  /* 学术 / 论文风:Times 系栈,系统自带,零外部字体依赖 */
  font-family: 'Times New Roman', 'Times', Georgia, serif;
  font-size: clamp(56px, 9vw, 128px);
  font-weight: 700;
  line-height: 1.05;
  letter-spacing: -0.02em;
  /* 跟随主题:亮模式 #212121(几乎纯黑)、暗模式 #f5f5f5(几乎纯白),
     不写死 #000 是为了避免暗模式下黑字落在黑底上。 */
  color: var(--color-text);
  margin: 0 0 32px;
}

.hero-subtitle {
  font-size: clamp(15px, 1.6vw, 18px);
  line-height: 1.6;
  color: var(--color-text-muted);
  margin: 0 0 36px;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  justify-content: center;
  margin-bottom: 32px;
}

/* Hero 里的终端演示窗
 *
 * 放在**左下角**。为什么是左边:标题块水平居中,长标题两侧留出空白,
 * 左下角那块既空着、又紧邻阅读起点,比右下角更"顺"。
 *
 * ---- 垂直位置怎么定的(不是随手填的数)----
 *
 * Hero 内容块约 537px 高、在视口里**垂直居中**,所以它下方那块空白只有
 * (H-537)/2 —— 900px 高的屏上也就 182px,比终端(≈221px)还矮。
 * 终端**不可能**靠"待在内容下方"避让,必须往上伸进内容所在的纵向区间,
 * 靠**横向错开**活下来。
 *
 * 横向是安全的:CTA 按钮 x≈650-790、副标题 x≈540-900(都是居中的窄块),
 * 终端 x≈65-505,都不碰。
 *
 * 硬约束是**标题底边** —— 它在 1440px 下横跨大半个屏幕,横向躲不开。
 * 标题底边离视口底 ≈ H/2 - 60.5(内容居中推出来的)。
 *
 * ⚠️ 这里有个**没法在本机验证**的变量:标题算 2 行还是 3 行。
 * 字体栈是 'Times New Roman', 'Times', Georgia, serif —— 前三个在 Linux 上
 * 全都没有,实际落到 **DejaVu Serif**,比 Times 宽一大截(实测 fc-match 确认)。
 * 换算下来「Manage Your Library」在 128px 下 ≈1336px,而可用宽度 1392px ——
 * **刚好卡在折与不折的边界上**,所以 2 行 / 3 行都可能:
 *     2 行 → 内容块 537px → 标题底边离底 390px(H=900)
 *     3 行 → 内容块 671px → 标题底边离底 322px(H=900)
 * 差 68px。**下面的数按 3 行(更坏的那种)定的**,所以真出现 2 行时
 * 只会更宽松,不会撞上。
 *
 * 终端总高 ≈ 319px(30 标题栏 + 12×1.65×13 正文 + 30 内边距 + 2 边框),
 * 于是 bottom + 319 必须 < 标题底边。
 *
 * 加高之后**上面已经很紧**:标题底边就是天花板,顶边只能贴着它。
 * 所以这轮加高主要是**往下长** —— bottom 从 10vh 收到 3.5vh,
 * 把多出来的 64px 高度放到下方,顶边只跟着抬了一点(311 → 350)。
 * 这样加高不会引入新的碰撞风险,顶边那道约束几乎没被触碰。
 *
 * ✅ 标题行数已确认(2026-09,用户实测):**2 行**。所以按
 *    标题底边 = H/2 - 60.5 = 389.5px(H=900)算,而不是保守的 322px。
 *    两者差 68px —— 这也是这轮能加这么高的原因。
 *
 * ⚠️ 坐标方向(改这个值之前先看这条):
 *    这里用 bottom 定位,而 bottom 是**离屏幕底边的距离** ——
 *    数值**越大越靠上**。想把它往上挪,是把这个值调**大**。
 *    (直觉上容易反,所以写在这。)
 *
 * 这一版又整体上移了 20px(bottom 31.5 → 51.3 @H=900):
 *     H=900  标题底边 389.5   bottom 51.3  顶边 370.3  余量 19px ✓
 *     H=881  标题底边 380.0   bottom 50.2  顶边 369.2  余量 11px ✓(最紧档)
 *     H=1080 标题底边 479.5   bottom 61.6  顶边 380.6  余量 99px ✓
 *     ⚠ 余量只剩 19px 了,再往上就要贴着标题 —— 想继续上移的话,
 *       要么降 --t-visible-lines(减高度),要么接受和标题挨得更近。
 *
 * ⚠️ 依赖:这个预算建立在"标题 2 行"上。而标题字体栈
 *    'Times New Roman', 'Times', Georgia, serif 在 Linux 上三个都落空、
 *    实际落到 DejaVu Serif(比 Times 宽),换算下来刚好卡在折行边界。
 *    如果哪天标题变成 3 行(换字体、改文案、窄一点的窗口),底边会掉到
 *    322px,终端就会压上去 —— 届时把 --t-visible-lines 降回 9 即可。
 * ============================================================ */
.hero-terminal {
  position: absolute;
  left: clamp(24px, 4.5vw, 88px);
  bottom: clamp(36px, 5.7vh, 110px);
  z-index: 2;
}

/* 两种情况下必须放弃绝对定位,回到文档流:
 *   1) 窄(<1100px):标题折成更多行、几乎占满宽度,横向错开不再成立
 *   2) 矮(<880px):居中块下沉,底部空间被吃掉
 *      —— 阈值从 720 提到 880 是被放大后的终端高度顶上去的,
 *         沿用 720 的话 1366×768 这类屏上会压在标题上
 * Hero 是 min-height: 100dvh(不是 height),回文档流后它自然长高,
 * 不会把内容挤出去。 */
@media (max-width: 1100px), (max-height: 880px) {
  .hero-terminal {
    position: static;
    margin-top: 20px;
  }
}

/* =============================================================
 * CTA primary  -  editorial 方角 + 内外双框
 *
 * 布局层级(从底到顶):
 *   .cta-primary(创建 stacking context,isolation: isolate)
 *   ├─ ::before  -  填充层,默认 0 宽,hover 时 scaleX(1)
 *   ├─ ::after   -  内框,1px border,inset 5px
 *   └─ 文字 + 箭头(自然层,在所有伪元素之上)
 *
 * 默认态:外框 1.5px + 内框 1px,中间 3.5px 留白(像书的封面 + 烫金内框)
 * hover:  填充层从左向右铺,内框反色成半透明白,文字 + 箭头反向
 * ============================================================= */
.cta-primary {
  position: relative;
  isolation: isolate;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 14px 24px;
  background: transparent;
  color: var(--color-accent);
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 17px;
  font-weight: 400;
  font-style: italic;
  letter-spacing: 0.005em;
  text-decoration: none;
  border: 1.5px solid var(--color-accent);
  border-radius: 0;
  overflow: hidden;
  cursor: pointer;
  transition:
    color 350ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 250ms cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 350ms cubic-bezier(0.22, 1, 0.36, 1);
}

/* 背景填充层(从左向右 scaleX) */
.cta-primary::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--color-accent);
  transform: scaleX(0);
  transform-origin: left center;
  transition: transform 450ms cubic-bezier(0.22, 1, 0.36, 1);
  z-index: -1;
}

/* 内层边框:1px 绿框,距外框 5px,中间留 3.5px 呼吸 */
.cta-primary::after {
  content: '';
  position: absolute;
  inset: 5px;
  border: 1px solid var(--color-accent);
  pointer-events: none;
  z-index: -1; /* 同 ::before 一层,DOM 顺序决定 ::after 叠在 ::before 之上 */
  transition: border-color 350ms cubic-bezier(0.22, 1, 0.36, 1);
}

.cta-primary:hover {
  color: #fff;
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(76, 175, 80, 0.3);
}

.cta-primary:hover::before {
  transform: scaleX(1);
}

/* hover 时内框反色成半透明白,跟绿底形成层次 */
.cta-primary:hover::after {
  border-color: rgba(255, 255, 255, 0.55);
}

.cta-primary:active {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(76, 175, 80, 0.2);
}

.cta-primary:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 4px;
}

/* 箭头 badge  -  圆形,跟方角按钮形成对比 */
.cta-arrow {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--color-accent);
  color: #fff;
  font-size: 12px;
  font-style: normal;
  line-height: 1;
  transition:
    background 350ms cubic-bezier(0.22, 1, 0.36, 1),
    color 350ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 350ms cubic-bezier(0.22, 1, 0.36, 1);
}

.cta-primary:hover .cta-arrow {
  background: #fff;
  color: var(--color-accent);
  transform: translateX(3px) scale(1.05);
}

/* =============================================================
 * Showcase  -  占位色块轮播(后续替换为真实图片)
 * ============================================================= */
.showcase {
  max-width: 1200px;
  margin: 0 auto;
  padding: clamp(60px, 10vw, 100px) 24px;
}

.showcase-stage {
  position: relative;
  margin-top: 48px;
}

.showcase-track {
  position: relative;
  width: 100%;
  height: 480px;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 16px 48px var(--color-shadow-strong);
}

.showcase-slide {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 600ms ease;
  pointer-events: none;
}

.showcase-slide.active {
  opacity: 1;
  pointer-events: auto;
}

.showcase-slide-content {
  text-align: center;
  color: #fff;
  padding: 32px;
  max-width: 640px;
}

.showcase-slide-tag {
  display: inline-block;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 13px;
  font-weight: 400;
  letter-spacing: 0.12em;
  padding: 5px 12px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 999px;
  margin-bottom: 20px;
  -webkit-backdrop-filter: blur(8px);
  backdrop-filter: blur(8px);
}

.showcase-slide-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 38px;
  font-weight: 400;
  letter-spacing: -0.02em;
  margin: 0 0 14px;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.showcase-slide-desc {
  font-size: 14px;
  opacity: 0.9;
  margin: 0;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.15);
}

.showcase-dots {
  display: flex;
  justify-content: center;
  gap: 8px;
  margin-top: 24px;
}

.showcase-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-border);
  border: none;
  padding: 0;
  cursor: pointer;
  transition: background 250ms ease, transform 250ms ease;
}

.showcase-dot:hover {
  background: var(--color-text-soft);
}

.showcase-dot.active {
  background: var(--color-accent);
  transform: scale(1.3);
}

@media (max-width: 768px) {
  .showcase-track {
    height: 360px;
    border-radius: 12px;
  }
  .showcase-slide-title {
    font-size: 26px;
  }
  .showcase-slide-content {
    padding: 24px;
  }
}

/* Hero decoration */
.hero-deco {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(76, 175, 80, 0.12) 0%,
    transparent 70%
  );
}

.deco-circle-1 {
  width: 400px;
  height: 400px;
  top: -100px;
  right: -120px;
}

.deco-circle-2 {
  width: 320px;
  height: 320px;
  bottom: -80px;
  left: -100px;
}

/* =============================================================
 * Features  -  左右交替布局(3 个 feature-row,隔行翻转)
 * 文字列(左/右)+ 视觉列(右/左)+ 三层重叠 z-index 骨架屏
 * ============================================================= */
.features {
  max-width: 1200px;
  margin: 0 auto;
  padding: clamp(60px, 10vw, 100px) 24px;
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: clamp(60px, 10vw, 100px);
  margin-top: 48px;
}

.feature-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: clamp(32px, 6vw, 80px);
  align-items: center;
}

/* 偶数行(0-indexed 的 1,3...):翻转视觉顺序,文字在右,图在左 */
.feature-row.reverse .feature-text-col {
  order: 2;
}

.feature-row.reverse .feature-visual-col {
  order: 1;
}

.feature-text-col {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.feature-icon-wrap {
  display: inline-grid;
  place-items: center;
  width: 56px;
  height: 56px;
  background: rgba(76, 175, 80, 0.1);
  color: var(--color-accent);
  border-radius: 12px;
  margin-bottom: 24px;
}

.feature-row .feature-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(28px, 3.5vw, 40px);
  font-weight: 400;
  letter-spacing: -0.02em;
  margin: 0 0 16px;
  color: var(--color-text);
}

.feature-row .feature-desc {
  font-size: 16px;
  line-height: 1.7;
  color: var(--color-text-muted);
  margin: 0;
  max-width: 480px;
}

/* =============================================================
 * Visual stage  -  3 层绝对定位叠在 relative 容器里
 * layer-1 底,layer-3 顶,中间错位让三层相互重叠
 * ============================================================= */
.feature-visual-stage {
  position: relative;
  width: 100%;
  aspect-ratio: 5 / 4;
}

.visual-layer {
  position: absolute;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 12px 32px var(--color-shadow-strong);
  background: var(--color-card);
}

.layer-1 {
  top: 0;
  left: 0;
  width: 75%;
  height: 75%;
  z-index: 1;
}

.layer-2 {
  top: 12%;
  right: 0;
  width: 70%;
  height: 75%;
  z-index: 2;
}

.layer-3 {
  bottom: 0;
  left: 18%;
  width: 60%;
  height: 55%;
  z-index: 3;
}

/* 骨架屏:1.6s 线性 shimmer 循环,模拟图片加载中 */
.skeleton-img {
  width: 100%;
  height: 100%;
  background: linear-gradient(
    110deg,
    var(--color-bg-alt) 8%,
    var(--color-border) 18%,
    var(--color-bg-alt) 33%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.6s linear infinite;
}

@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@media (max-width: 900px) {
  .feature-row {
    grid-template-columns: 1fr;
    gap: 32px;
  }
  /* 移动端取消翻转,统一文字在上、图片在下 */
  .feature-row.reverse .feature-text-col {
    order: 0;
  }
  .feature-row.reverse .feature-visual-col {
    order: 0;
  }
  .feature-visual-stage {
    aspect-ratio: 4 / 3;
  }
}

/* =============================================================
 * Element Plus overrides  -  把 el-tag 拉到项目 token
 * ============================================================= */
.section-tag.el-tag {
  background: transparent !important;
  border-color: var(--color-accent) !important;
  color: var(--color-accent) !important;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  font-weight: 600;
  margin-bottom: 16px;
}

/* =============================================================
 * Learning Log
 * ============================================================= */
.learning-log {
  max-width: 1200px;
  margin: 0 auto;
  padding: clamp(60px, 10vw, 100px) 24px;
}

.learning-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  margin-top: 48px;
}

.learning-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
  padding: 32px 28px;
  background: var(--color-card);
  border: 2px dashed var(--color-border);
  border-radius: 16px;
  text-align: left;
  transition: border-color 250ms ease, transform 250ms ease;
}

.learning-card:hover {
  border-color: var(--color-accent);
  transform: translateY(-4px);
}

.learning-card-icon {
  display: inline-grid;
  place-items: center;
  width: 48px;
  height: 48px;
  background: rgba(76, 175, 80, 0.1);
  border-radius: 12px;
  font-size: 24px;
  line-height: 1;
  margin-bottom: 4px;
}

.learning-card-tag {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-accent);
}

.learning-card-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.015em;
  margin: 0;
  color: var(--color-text);}

.learning-card-body {
  font-size: 14px;
  line-height: 1.65;
  color: var(--color-text-muted);
  margin: 0;
  flex: 1;
}

.learning-card-status {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  align-self: flex-start;
  padding: 4px 10px;
  background: rgba(76, 175, 80, 0.12);
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  border-radius: 999px;
}

/* ============================================================
 * 学习经历卡片 —— 从"占位"变成"可点入口"
 *
 * 原来三张卡是 learning-card-stub(虚线框 + "内容待添加"),
 * 现在内容的真身在 /journey,首页只留三张分类摘要卡:
 *   - 虚线框改实线 + 左侧分类色轨(和详情页的分类色编码一致)
 *   - 整卡可点(键盘也能进),hover 抬起
 * ============================================================ */
.learning-card.is-clickable {
  border-style: solid;
  border-left: 3px solid var(--c, var(--color-border));
  cursor: pointer;
}

.learning-card.is-clickable:hover {
  transform: translateY(-3px);
  border-color: var(--c, var(--color-accent));
}

.learning-card.is-clickable:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--color-accent);
}

/* 图标和分类标签跟着分类色走,和 /journey 详情页保持同一套编码 */
.learning-card.is-clickable .learning-card-icon {
  color: var(--c, var(--color-text));
}

.learning-card.is-clickable .learning-card-tag {
  color: var(--c, var(--color-text-soft));
}

/* "查看详情"推到卡片底部 —— 三张卡文字长度不同,不推的话箭头参差不齐 */
.learning-card-status.is-ready {
  margin-top: auto;
  padding: 0;
  background: transparent;
}

/* 正文里的跳转链接 */
.learning-link {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 0;
  background: none;
  border: none;
  border-bottom: 1px solid transparent;
  color: var(--color-accent);
  font-family: inherit;
  font-size: 13px;
  font-style: normal;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 200ms ease;
}

.learning-link:hover {
  border-bottom-color: var(--color-accent);
}

.learning-note {
  margin: 32px auto 0;
  max-width: 640px;
  text-align: center;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-soft);
  font-style: italic;
}

@media (max-width: 900px) {
  .learning-grid {
    grid-template-columns: 1fr;
  }
}

/* =============================================================
 * Project Structure  -  项目结构 / 目录树 / 学习路线 / 下一步
 *
 * 四段自上而下:入口卡 → 目录树 → 学习路线 → AI Agent 方向。
 * 视觉沿用本页既有语言(卡片 + 1px 描边 + hover 抬起 + 分类色),
 * 不引入新配色 —— 这一区块是"信息密度高"的,颜色再花就糊了。
 * ============================================================= */
.ps {
  max-width: 1200px;
  margin: 0 auto;
  padding: clamp(60px, 10vw, 100px) 24px;
}

/* ---------- 三个入口 ---------- */
.ps-entries {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 48px;
}

.ps-entry {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 10px;
  padding: 26px 24px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  box-shadow: var(--shadow-card);
  text-align: left;
  text-decoration: none;
  color: inherit;
  /* button 元素要显式继承字体,否则会掉回浏览器默认的 UI 字体 */
  font-family: inherit;
  cursor: pointer;
  transition: transform 220ms ease, border-color 220ms ease, box-shadow 220ms ease;
}

.ps-entry:hover {
  transform: translateY(-3px);
  border-color: var(--color-accent);
  box-shadow: var(--shadow-card-hover);
}

.ps-entry:focus-visible {
  outline: none;
  box-shadow: 0 0 0 2px var(--color-accent);
}

.ps-entry-icon {
  display: inline-grid;
  place-items: center;
  width: 42px;
  height: 42px;
  background: rgba(76, 175, 80, 0.1);
  color: var(--color-accent);
  border-radius: 11px;
  font-size: 21px;
  line-height: 1;
}

.ps-entry-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-text-soft);
}

.ps-entry-value {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text);
  /* 长仓库名要能断,不然卡片被撑破 */
  word-break: break-word;
}

.ps-entry-go {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: auto;
  padding-top: 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-accent);
}

/* 箭头在 hover 时往右挪一点 —— 暗示"会跳走" */
.ps-entry:hover .ps-entry-go .el-icon {
  transform: translateX(3px);
}

.ps-entry-go .el-icon {
  transition: transform 200ms ease;
}

/* ---------- 区块小标题(目录树 / 学习路线 / 下一步共用) ---------- */
.ps-h3 {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 24px;
  font-weight: 700;
  letter-spacing: -0.015em;
  margin: 0 0 6px;
  color: var(--color-text);
}

.ps-h3-sub {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-muted);
}

.ps-tree-block,
.ps-roadmap-block,
.ps-next-block {
  margin-top: 72px;
}

/* ---------- 目录树 ---------- */
.ps-tree {
  margin: 24px 0 0;
  padding: 26px 26px 22px;
  background: var(--color-bg-alt);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  /* 等宽字族统一走 token —— 见 theme.css --font-mono */
  font-family: var(--font-mono);
  font-size: 12.5px;
  /* 行高涨到 1.95:树形符号 ├ │ └ 本身笔画就密,行距不够会糊成一片 */
  line-height: 1.95;
  /* 窄屏横向滚动,不折行 —— 折了树就断了 */
  overflow-x: auto;
}

.ps-row {
  display: flex;
  align-items: baseline;
  gap: 16px;
  /* 每行是一个 flex 行,但分支前缀用 white-space:pre 保留前导空格,
     ├ │ └ 仍然逐行垂直对齐 */
  white-space: nowrap;
  /* max-content:让每行按内容宽撑开,父级 .ps-tree 才能算出正确的
     scrollWidth 去横向滚动。不写的话 flex 行会缩到容器宽,
     内容溢出但行盒不宽,滚动条的滚动范围会不对 */
  width: max-content;
  min-width: 100%;
}

/* 树形前缀压暗 —— 结构线不该和内容抢注意力 */
.ps-branch {
  flex-shrink: 0;
  color: var(--color-text-soft);
  white-space: pre;
  opacity: 0.8;
}

.ps-name {
  color: var(--color-text-muted);
  font-weight: 500;
}

/* 顶层节点(仓库根 / 两个工程)亮一档 —— 一眼看到前后端的分界 */
.ps-row.is-root .ps-name {
  color: var(--color-text);
  font-weight: 600;
}

.ps-note {
  color: var(--color-text-soft);
  font-size: 12px;
}

/* ---------- 学习路线 ---------- */
.ps-roadmap {
  list-style: none;
  padding: 0;
  margin: 28px 0 0;
}

.ps-step {
  position: relative;
  display: grid;
  grid-template-columns: 56px 1fr;
  gap: 18px;
  padding: 14px 0;
}

/* 阶段之间的连接竖线 —— 圆心在 28px(56px 列宽的中点) */
.ps-step:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 28px;
  top: 54px;
  bottom: -4px;
  width: 1px;
  background: var(--color-border);
  transform: translateX(-50%);
}

.ps-step-num {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 26px;
  line-height: 1.1;
  text-align: center;
  color: var(--color-text-soft);
}

/* 还没做的阶段用 accent 标出来 —— 视线自然落到"下一步" */
.ps-step.is-next .ps-step-num {
  color: var(--color-accent);
}

.ps-step-body {
  padding-top: 2px;
}

.ps-step-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin: 0 0 6px;
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text);
}

.ps-step-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 9px;
  background: rgba(76, 175, 80, 0.12);
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  border-radius: 999px;
}

/* 进行中:描边而不是实底 —— 和"已落地"区分开,又不用第二种颜色 */
.ps-step-badge.is-next {
  background: transparent;
  border: 1px dashed var(--color-accent);
}

.ps-step-desc {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-muted);
  max-width: 760px;
}

/* ---------- 下一步:AI Agent ---------- */
.ps-next-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 32px;
}

.ps-next-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 28px 24px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  box-shadow: var(--shadow-card);
  transition: transform 220ms ease, border-color 220ms ease, box-shadow 220ms ease;
}

.ps-next-card:hover {
  transform: translateY(-3px);
  border-color: var(--color-accent);
  box-shadow: var(--shadow-card-hover);
}

.ps-next-icon {
  display: inline-grid;
  place-items: center;
  width: 46px;
  height: 46px;
  background: rgba(76, 175, 80, 0.1);
  color: var(--color-accent);
  border-radius: 12px;
  font-size: 23px;
  line-height: 1;
}

.ps-next-title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text);
}

.ps-next-desc {
  margin: 0;
  flex: 1;
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-text-muted);
}

.ps-next-tag {
  padding: 3px 10px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--color-text-soft);
}

@media (max-width: 900px) {
  .ps-entries,
  .ps-next-grid {
    grid-template-columns: 1fr;
  }

  .ps-tree-block,
  .ps-roadmap-block,
  .ps-next-block {
    margin-top: 56px;
  }
}

@media (max-width: 600px) {
  /* 树:字号收一档 + 内边距压小,争取少横向滚一点 */
  .ps-tree {
    padding: 20px 16px 16px;
    font-size: 11.5px;
  }

  .ps-row {
    gap: 12px;
  }

  .ps-step {
    grid-template-columns: 40px 1fr;
    gap: 14px;
  }

  .ps-step-num {
    font-size: 20px;
  }

  /* 列宽变了,连接线的圆心也要跟着挪到 20px */
  .ps-step:not(:last-child)::before {
    left: 20px;
    top: 46px;
  }
}

/* =============================================================
 * Join Us  -  邀请贡献者
 *
 * 语气上刻意"收着":陈述而不是号召,邀请而不是推销。
 * 视觉跟着这个语气走,所以做了三处调整:
 *   1. 序号从 36px 大号衬线数字收成 12px 等宽标签 + 一条渐隐引线
 *      —— 原来那个尺寸在抢标题的视线,现在它只是个索引;
 *   2. 卡片补上项目通用的抬升语言(多层阴影 + 受光棱边),
 *      和 ps-* / learning-card 一致,不再是"扁平描边";
 *   3. CTA 的大号斜体口号换成正常的衬线段落 —— 斜体 + 大字号
 *      本身就是"标语"的视觉语法,和克制的文案冲突。
 * ============================================================= */
.join-list {
  list-style: none;
  padding: 0;
  margin: 48px 0 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.join-item {
  position: relative;
  display: flex;
  flex-direction: column;
  /* stretch 而不是 flex-start:去掉 .join-item-body 包装后,标题/正文成了
     直接子元素,若用 flex-start 它们的宽度会退化成 fit-content ——
     对长文本虽然最终也等于可用宽度,但不如显式撑满稳当 */
  align-items: stretch;
  padding: 30px 26px 32px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  box-shadow: var(--shadow-card), var(--edge-highlight);
  /* 顶部细轨要贴圆角裁切 */
  overflow: hidden;
  transition: transform 250ms ease, border-color 250ms ease, box-shadow 250ms ease;
}

.join-item:hover {
  transform: translateY(-3px);
  border-color: var(--color-accent);
  box-shadow: var(--shadow-card-hover), var(--edge-highlight);
}

/* 顶部一条 2px accent 轨,hover 时从左展开到满宽 ——
   和导航下拉项的左侧竖条是同一套"指示器"语汇,只是换了个方向 */
.join-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 0;
  height: 2px;
  background: var(--color-accent);
  transition: width 260ms cubic-bezier(0.22, 0.61, 0.36, 1);
}

.join-item:hover::before {
  width: 100%;
}

/* 序号 + 引出线 —— 引出线往右渐隐,把视线交给标题 */
.join-num {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.18em;
  color: var(--color-accent);
}

.join-num::after {
  content: '';
  flex: 1;
  height: 1px;
  background: linear-gradient(to right, var(--color-border), transparent);
}

.join-item-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: -0.015em;
  margin: 0 0 10px;
  color: var(--color-text);
}

.join-item-desc {
  font-size: 14px;
  line-height: 1.75;
  color: var(--color-text-muted);
  margin: 0;
}

/* CTA 区域  -  克制的一段说明 + 一主一辅两个入口 */
.join-cta {
  position: relative;
  margin-top: 56px;
  padding: 52px 32px 48px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  box-shadow: var(--shadow-card), var(--edge-highlight);
  text-align: center;
  overflow: hidden;
}

/* 顶部中间一小段 accent 光带 —— 给 CTA 一点"被点亮"的感觉,
   但不铺满底色(铺满就成了促销页,和文案的语气打架) */
.join-cta::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 180px;
  height: 2px;
  background: linear-gradient(to right, transparent, var(--color-accent), transparent);
}

.join-cta-text {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(19px, 2.2vw, 26px);
  font-weight: 400;
  line-height: 1.6;
  letter-spacing: -0.01em;
  color: var(--color-text);
  margin: 0 auto 32px;
  max-width: 640px;
}

.join-cta-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 14px;
}

.join-cta-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 14px 30px;
  background: var(--color-accent);
  color: #fff;
  font-family: inherit;
  font-size: 15px;
  font-weight: 600;
  text-decoration: none;
  border: 1px solid var(--color-accent);
  border-radius: 999px;
  cursor: pointer;
  transition:
    background 220ms ease,
    border-color 220ms ease,
    color 220ms ease,
    transform 220ms ease,
    box-shadow 220ms ease;
}

.join-cta-btn:hover {
  background: var(--color-accent-hover);
  border-color: var(--color-accent-hover);
  transform: translateY(-2px);
  box-shadow: 0 10px 26px rgba(76, 175, 80, 0.28);
}

/* 次按钮:描边款,视觉权重低一档 —— "先读 README"该是更轻的那条路 */
.join-cta-btn.is-ghost {
  background: transparent;
  color: var(--color-text);
  border-color: var(--color-border);
}

.join-cta-btn.is-ghost:hover {
  background: transparent;
  color: var(--color-accent);
  border-color: var(--color-accent);
  box-shadow: none;
}

.join-cta-btn .cta-arrow {
  font-size: 15px;
  transition: transform 220ms ease;
}

.join-cta-btn:hover .cta-arrow {
  transform: translateX(3px);
}

/* =============================================================
 * 项目贡献者  -  名录墙
 *
 * 紧跟其后的 SpecialThanks 是一条全宽滚动跑马灯 + 斜纹警戒带,那是整页
 * 最"响"的一块。这里刻意反过来:静态、细线、低对比 —— 两块都抢眼等于
 * 都不抢眼,后一块也就白做了。
 *
 * 特效只有一条主线:一道缓慢扫过的光带(复用页面既有的 skeleton-shimmer),
 * 并用 --i 给每个槽位**错开相位**,让整面墙是"涟漪"而不是"齐闪"。
 * 用负延迟而不是正延迟:正延迟会让槽位在等待期间停在未动画的初始态
 * (头一秒看着像坏了),负延迟直接从中途起播,一开始就是错开的。
 * ============================================================= */
.contributors {
  margin-top: 72px;
  text-align: center;
}

.contributors-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(20px, 2.2vw, 24px);
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0 0 8px;
  color: var(--color-text);
}

.contributors-subtitle {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-text-muted);
  margin: 0 0 32px;
}

.contributors-grid {
  list-style: none;
  padding: 0;
  margin: 0 auto;
  display: grid;
  /* 定宽列 + 居中:6 个槽位排成一面整齐的墙,而不是被 flex 拉伸得忽宽忽窄 */
  grid-template-columns: repeat(6, 88px);
  justify-content: center;
  gap: 20px;
}

.contributor {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 14px 8px 12px;
  border: 1px solid transparent;
  border-radius: 12px;
  cursor: default;
  transition: transform 260ms ease, border-color 260ms ease, background 260ms ease;
}

/* hover:槽位浮起 + 现出一圈细描边。空态槽位也响应 ——
   让"这里是放人的地方"这件事被看见 */
.contributor:hover {
  transform: translateY(-3px);
  border-color: var(--color-border);
  background: var(--color-card);
}

.contributor-link {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: inherit;
}

.contributor-avatar {
  display: block;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--color-bg-alt);
  border: 1px solid var(--color-border);
  transition: border-color 260ms ease, transform 260ms ease;
}

.contributor:hover .contributor-avatar {
  border-color: var(--color-accent);
  transform: scale(1.04);
}

.contributor-name {
  max-width: 72px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 错开相位的扫光 —— calc(2 * -180ms) = -360ms */
.contributor .skeleton-circle,
.contributor .skeleton-bar {
  animation-delay: calc(var(--i, 0) * -180ms);
}

/* 圆形头像:56px,跟用户要求「不要太大」匹配 */
.skeleton-circle {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(
    110deg,
    var(--color-bg-alt) 8%,
    var(--color-border) 18%,
    var(--color-bg-alt) 33%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.6s linear infinite;
}

/* 名字条:跟头像同宽,4px 圆角 */
.skeleton-bar {
  width: 56px;
  height: 9px;
  border-radius: 4px;
  background: linear-gradient(
    110deg,
    var(--color-bg-alt) 8%,
    var(--color-border) 18%,
    var(--color-bg-alt) 33%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.6s linear infinite;
}

@media (max-width: 900px) {
  .join-list {
    grid-template-columns: 1fr;
  }
  .join-cta {
    padding: 36px 24px;
  }
  .contributors-grid {
    gap: 20px 16px;
  }
  .contributor {
    padding: 10px 6px;
  }
}

@media (max-width: 600px) {
  /* 6 列在 375px 上每格只剩 ~44px,头像会挤成一片 —— 改成 3×2 两行 */
  .contributors-grid {
    grid-template-columns: repeat(3, 1fr);
    max-width: 300px;
  }
  .skeleton-circle {
    width: 48px;
    height: 48px;
  }
  .skeleton-bar {
    width: 48px;
  }
}

/* =============================================================
 * Contact  -  联系方式
 *
 * 首版是一列竖排(图标 / 标签 / 值),三张卡都是"三行一样高"。
 * 现在值可能不止一个(邮箱有两个地址),竖排会把卡撑得很高、
 * 三张卡高度还参差不齐 —— 所以改成"卡片头 + 分隔线 + 值列表":
 *
 *   ┌──────────────────────┐
 *   │ [图标] 邮箱           │  ← 头部一行(横排,省一层高度)
 *   │ ──────────────────── │  ← 细分隔线
 *   │ 98206858@qq.com      │  ← 值列表,有几个放几个
 *   │ tangmingtao111.@...  │
 *   └──────────────────────┘
 *
 * 三张卡用 grid 等高,内部值列表 flex:1 → 底边天然对齐。
 *
 * 特效四层,全部挂在项目已有的交互语汇上,不新造一套:
 *   1. 图标底色 / hover 描边跟着 --c(辅助色相)走
 *   2. 卡内一层极淡的 --c 径向光晕,hover 淡入
 *   3. 值文字下划线从左往右扫过 —— 只有真 <a> 才有
 *   4. hover 抬起 + 描边变色 —— 只有含链接的卡才有
 *
 * 光晕用 ::before + opacity 过渡,而不是直接过渡 background-image:
 * 两个渐变之间没法插值,直接换 background-image 会"啪"地跳变。
 * ============================================================= */
.contact {
  max-width: 1200px;
  margin: 0 auto;
  padding: clamp(60px, 10vw, 100px) 24px;
}

.contact-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  /* 等高:三张卡的值数量不同(2 / 1 / 1),不拉平的话底边会参差 */
  align-items: stretch;
  gap: 20px;
  max-width: 940px;
  margin: 0 auto;
}

.contact-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 24px 24px 26px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 14px;
  box-shadow: var(--shadow-card), var(--edge-highlight);
  /* 兜底色相:万一漏了 is-* 类,也不会拿到一个空的 var(--c) */
  --c: var(--color-accent);
  /* 光晕要贴圆角裁切 */
  overflow: hidden;
  transition: transform 220ms ease, border-color 220ms ease, box-shadow 220ms ease;
}

.contact-card.is-mail {
  --c: var(--hue-amber);
}

.contact-card.is-github {
  --c: var(--hue-teal);
}

.contact-card.is-project {
  --c: var(--hue-moss);
}

/* 光晕 —— 从卡片顶边往下洇开的一层 --c 淡色 */
.contact-card::before {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(
    120% 80% at 50% 0%,
    color-mix(in srgb, var(--c) 14%, transparent) 0%,
    transparent 68%
  );
  opacity: 0;
  pointer-events: none;
  transition: opacity 260ms ease;
}

.contact-card:hover::before {
  opacity: 1;
}

/* 抬起只给"卡里有链接"的卡 —— 纯展示的卡不该有"我能点"的反馈 */
.contact-card.has-links:hover {
  transform: translateY(-3px);
  border-color: var(--c);
  box-shadow: var(--shadow-card-hover), var(--edge-highlight);
}

/* ---------- 卡片头:图标 + 标签横排 ---------- */
.contact-head {
  position: relative; /* 压在光晕之上 */
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 14px;
  margin-bottom: 16px;
  /* 分隔线用 border 而不是 ::after —— 少一个伪元素,也不用管定位 */
  border-bottom: 1px solid var(--color-border);
}

.contact-icon {
  display: inline-grid;
  place-items: center;
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--c) 12%, transparent);
  color: var(--c);
  font-size: 17px;
  line-height: 1;
  transition: background 220ms ease, transform 220ms ease;
}

.contact-card.has-links:hover .contact-icon {
  background: color-mix(in srgb, var(--c) 20%, transparent);
  transform: translateY(-1px) scale(1.05);
}

.contact-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-text-soft);
}

/* ---------- 值列表 ---------- */
.contact-values {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  /* 撑满剩余高度 —— 配合 grid 的 stretch 让三张卡底边对齐 */
  flex: 1;
}

.contact-value {
  position: relative;
  align-self: flex-start;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 17px;
  line-height: 1.45;
  color: var(--color-text);
  text-decoration: none;
  /* 邮箱和仓库名都是长串,不给断行会顶出卡片。
     anywhere 而不是 break-word:后者对"没有空格的长串"不一定生效 */
  overflow-wrap: anywhere;
  transition: color 220ms ease;
}

/* 下划线扫过 —— 定义在值上,但只有 a.contact-value:hover 才触发;
   非链接的值 scaleX 始终是 0,看不见 */
.contact-value::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -2px;
  height: 1px;
  background: var(--c);
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 300ms cubic-bezier(0.22, 0.61, 0.36, 1);
}

a.contact-value:hover {
  color: var(--c);
}

a.contact-value:hover::after {
  transform: scaleX(1);
}

a.contact-value:focus-visible {
  outline: 2px solid var(--c);
  outline-offset: 3px;
  border-radius: 2px;
}

@media (max-width: 900px) {
  .contact-grid {
    grid-template-columns: 1fr;
  }
}

/* =============================================================
 * Footer
 * ============================================================= */
.landing-footer {
  margin-top: 80px;
  background: var(--color-footer-bg);
  color: var(--color-footer-text);
}

.footer-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 56px 24px 32px;
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 32px;
}

.footer-logo {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 24px;
  font-weight: 400;
  color: var(--color-accent);
  margin-bottom: 12px;
}

.footer-tagline {
  font-family: 'DM Serif Display', Georgia, serif;
  font-style: italic;
  font-size: 15px;
  opacity: 0.8;
  margin: 0;
}

.footer-col h4 {
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin: 0 0 12px;
  opacity: 0.7;
}

.footer-col p {
  font-size: 13px;
  margin: 4px 0;
  opacity: 0.85;
}

/* 页脚里唯一的链接(GitHub)。
   页脚两种主题下都是深色底(--color-footer-bg 亮色 #212121 / 暗色 #0d0d0d),
   不给规则的话 <a> 会用浏览器默认的蓝 #0000EE —— 深底上几乎看不见。
   所以颜色继承页脚文字,只用一条下划线表示可点。 */
.footer-col a {
  color: inherit;
  text-decoration: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.28);
  transition: color 180ms ease, border-color 180ms ease;
}

.footer-col a:hover {
  color: var(--color-accent);
  border-color: var(--color-accent);
}

.footer-bottom {
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  padding: 20px 24px;
  text-align: center;
  font-size: 13px;
  opacity: 0.7;
}

@media (max-width: 768px) {
  .footer-inner {
    grid-template-columns: 1fr;
    gap: 24px;
  }
}
</style>