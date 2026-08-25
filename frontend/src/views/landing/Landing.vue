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
 * 3. Features  -  3 张卡片(图书/订单/用户)
 * 4. Usage  -  快速开始 + 常见问题
 * 5. Learning Log  -  学习经历占位
 * 6. Contact  -  联系方式
 * 7. Footer  -  版权 + 联系方式 + 备案号
 *
 * Overlays:
 * - LoadingScreen  -  首屏
 * - BackToTop  -  滚动后右下角
 */
import { ref, onMounted, onUnmounted } from 'vue'
import LandingNav from './LandingNav.vue'
import LoadingScreen from './LoadingScreen.vue'
import BackToTop from './BackToTop.vue'

const heroTitle = 'Manage Your Library Beautifully'

// 标题字母动画数据  -  IIFE 只算一次,random 位置固定下来
// - inDelay: phase 1(中间→两边展开)的延迟
// - startX/Y: phase 1 起始的随机偏移(中间区域内的随机点)
// - outDelay: phase 2(从左往右浮起波)的延迟
const titleChars = (() => {
  const chars = heroTitle.split('')
  const len = chars.length
  const center = (len - 1) / 2

  return chars.map((char, i) => {
    // 入场延迟:距离字符串中心越远,延迟越大(中间先出),最大 300ms
    const distFromCenter = Math.abs(i - center)
    const maxDist = center
    const inDelay = Math.round((distFromCenter / maxDist) * 300)

    // 起始位置:全部集中在标题中间区域随机分布(±100px 横向,±30px 纵向)
    const startX = Math.round((Math.random() - 0.5) * 200)
    const startY = Math.round((Math.random() - 0.5) * 60)

    return {
      char: char === ' ' ? ' ' : char,
      startX,
      startY,
      inDelay,
    }
  })
})()

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

const usage = [
  {
    q: '第一次使用从哪开始?',
    a: '用 BOSS 账号登录后台,进入「图书管理」录入图书,再进入「下单」生成订单。',
  },
  {
    q: '忘记密码怎么办?',
    a: '联系 BOSS 管理员,在用户管理页重置密码;或联系技术支持邮箱。',
  },
  {
    q: '数据安全吗?',
    a: 'JWT + BCrypt 双重保护;敏感字段(password / salt)永不返回前端;HTTPS 传输加密。',
  },
]

/* 快速开始 3 步  -  el-steps 数据 */
const usageSteps = [
  { title: '登录后台', desc: '使用 BOSS 账号登录,获取 JWT token。' },
  { title: '录入图书', desc: '在「图书管理」新增图书,填写 ISBN / 价格 / 库存。' },
  { title: '生成订单', desc: '在「下单」选图书填数量,提交后系统记录订单快照。' },
]

const currentYear = new Date().getFullYear()

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
  <LoadingScreen />

  <a href="#landing-main" class="skip-link">跳过导航,直达主内容</a>

  <LandingNav />

  <main id="landing-main" class="landing">
    <!-- ============== Hero ============== -->
    <section id="home" class="hero">
      <div class="hero-inner">
        <div class="hero-eyebrow">
          Spring Boot · Vue 3 · Element Plus
        </div>

        <h1 class="hero-title">
          <span
            v-for="(item, i) in titleChars"
            :key="i"
            class="hero-char"
            :style="{
              '--start-x': item.startX + 'px',
              '--start-y': item.startY + 'px',
              '--in-delay': item.inDelay + 'ms',
            }"
          >{{ item.char }}</span>
        </h1>

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

    <!-- ============== Usage ============== -->
    <section id="quick-start" class="usage" v-reveal>
      <header class="section-head">
        <h2 class="section-title">快速开始</h2>
        <p class="section-sub">三步上手</p>
      </header>

      <ol class="usage-steps">
        <el-steps direction="vertical" :space="80" finish-status="success">
          <el-step
            v-for="(step, i) in usageSteps"
            :key="i"
            :title="step.title"
            :description="step.desc"
          />
        </el-steps>
      </ol>

      <div id="faq" class="faq">
        <h3 class="faq-title">常见问题</h3>
        <el-collapse>
          <el-collapse-item
            v-for="(item, i) in usage"
            :key="i"
            :name="i"
            :title="item.q"
          >
            <p class="faq-answer">{{ item.a }}</p>
          </el-collapse-item>
        </el-collapse>
      </div>
    </section>

    <!-- ============== Learning Log ============== -->
    <section id="learning-log" class="learning-log" v-reveal>
      <header class="section-head">
        <h2 class="section-title">学习经历</h2>
        <p class="section-sub">开发此项目时遇到的问题、踩过的坑、学到的知识点</p>
      </header>

      <div class="learning-grid">
        <article class="learning-card learning-card-stub">
          <span class="learning-card-icon" aria-hidden="true">🛠</span>
          <span class="learning-card-tag">Technical</span>
          <h3 class="learning-card-title">技术难点 · 待添加</h3>
          <p class="learning-card-body">
            在这里写一句概括这个问题是什么、卡在哪里、最终怎么解决  -  -  让读者三秒内 get 到价值。
          </p>
          <span class="learning-card-status">内容待添加</span>
        </article>

        <article class="learning-card learning-card-stub">
          <span class="learning-card-icon" aria-hidden="true">🏗</span>
          <span class="learning-card-tag">Architecture</span>
          <h3 class="learning-card-title">架构决策 · 待添加</h3>
          <p class="learning-card-body">
            记录选了什么、放弃了什么、理由是什么(性能 / 可维护性 / 学习成本的取舍)。
          </p>
          <span class="learning-card-status">内容待添加</span>
        </article>

        <article class="learning-card learning-card-stub">
          <span class="learning-card-icon" aria-hidden="true">📚</span>
          <span class="learning-card-tag">Skill Up</span>
          <h3 class="learning-card-title">学到的技能 · 待添加</h3>
          <p class="learning-card-body">
            这次开发新掌握了什么  -  -  一句话描述 + 在项目里怎么用上的。
          </p>
          <span class="learning-card-status">内容待添加</span>
        </article>
      </div>

      <p class="learning-note">
        以上是占位结构,展示将来学习笔记的三个分类。开始写第一篇时,把对应卡片替换成实际内容即可。
      </p>
    </section>

    <!-- ============== Join Us ============== -->
    <section id="join-us" class="join-us" v-reveal>
      <header class="section-head">
        <h2 class="section-title">加入我们</h2>
        <p class="section-sub">
          加入我们,释放你的大脑,扩展这个项目,学习别人代码,弄懂架构思想,展示你的设计理念,开源你的无穷想法,让这个项目更加的 NB、健全、完善。同时你也可以写在你的简历上。
        </p>
      </header>

      <ol class="join-list">
        <li class="join-item">
          <span class="join-num">01</span>
          <div class="join-item-body">
            <h3 class="join-item-title">有想法的人</h3>
            <p class="join-item-desc">
              愿意让自己的想法被他人看见和认同。在这里把想法变成可运行的代码,被真实用户验证。
            </p>
          </div>
        </li>
        <li class="join-item">
          <span class="join-num">02</span>
          <div class="join-item-body">
            <h3 class="join-item-title">学习代码和项目结构的人</h3>
            <p class="join-item-desc">
              你在这里阅读别人的代码,理解项目的层级和分区,有助于你理解一个真正的项目如何做。
            </p>
          </div>
        </li>
        <li class="join-item">
          <span class="join-num">03</span>
          <div class="join-item-body">
            <h3 class="join-item-title">想在简历里面写点东西的人</h3>
            <p class="join-item-desc">
              这里不需要你从零做起,看懂前人代码,解决历史遗留 Bug,增加你的内容(一个完整到令面试官都喜欢的业务)。
            </p>
          </div>
        </li>
      </ol>

      <div class="join-cta">
        <p class="join-cta-text">
          还等什么,如果你符合这样的条件,还等什么,加入我们吧,这里将是展示你无限可能的地方!
        </p>
        <a
          href="https://github.com/"
          target="_blank"
          rel="noopener noreferrer"
          class="join-cta-btn"
        >
          立即加入
        </a>
      </div>

      <!-- Sponsors  -  头像 + 名字骨架,目前未接 GitHub API -->
      <div class="sponsors">
        <h3 class="sponsors-title">项目 Sponsors</h3>
        <p class="sponsors-subtitle">感谢以下支持者让这个项目走得更远</p>

        <ul class="sponsors-grid" aria-label="Sponsors">
          <li v-for="i in 6" :key="i" class="sponsor-card">
            <div class="sponsor-avatar skeleton-circle" aria-hidden="true" />
            <div class="sponsor-name-skeleton skeleton-bar" aria-hidden="true" />
          </li>
        </ul>

        <p class="sponsors-note">
          接入 GitHub Sponsors API 后,这里会自动展示真实头像和名字。
        </p>
      </div>
    </section>

    <!-- ============== Contact ============== -->
    <section id="contact" class="contact" v-reveal>
      <header class="section-head">
        <h2 class="section-title">联系方式</h2>
      </header>

      <div class="contact-grid">
        <a class="contact-card" href="mailto:tmt@example.com">
          <span class="contact-label">邮箱</span>
          <span class="contact-value">tmt@example.com</span>
        </a>
        <a class="contact-card" href="https://github.com/" target="_blank">
          <span class="contact-label">GitHub</span>
          <span class="contact-value">@tangmingtao</span>
        </a>
        <div class="contact-card">
          <span class="contact-label">项目</span>
          <span class="contact-value">springboot3-vue3-elementui</span>
        </div>
      </div>
    </section>

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
          <p>GitHub: @tangmingtao</p>
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
  font-weight: 700;
  letter-spacing: -0.04em;
  margin: 0 0 8px;
  */
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
  padding: clamp(80px, 14vw, 160px) 24px clamp(60px, 10vw, 120px);
  text-align: center;
  overflow: hidden;
}

.hero-inner {
  max-width: 880px;
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
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(36px, 6vw, 64px);
  font-weight: 400; /* DM Serif Display 只有 400,本身就是 dramatic 饱满展示字 */
  line-height: 1.1;
  letter-spacing: -0.025em;
  color: var(--color-text);
  margin: 0 0 24px;
}

.hero-char {
  display: inline-block;
  opacity: 0;
  /* 单一入场动画:字母从中间随机位置飞到最终位置(中间字母先动,合计 ~1.5s) */
  animation: char-in 1200ms cubic-bezier(0.22, 1, 0.36, 1) var(--in-delay, 0ms) forwards;
  color: var(--color-accent);
}

.hero-char:nth-child(3n) {
  color: var(--color-text);
}

/* 入场:字母从中间随机位置飞到最终位置(中间先出),700ms 后全部就位 */
@keyframes char-in {
  0% {
    opacity: 0;
    transform: translate(var(--start-x, 0px), var(--start-y, 0px)) scale(0.6);
  }
  60% {
    opacity: 1;
  }
  100% {
    opacity: 1;
    transform: translate(0, 0) scale(1);
  }
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
  animation: float-1 12s ease-in-out infinite;
}

.deco-circle-2 {
  width: 320px;
  height: 320px;
  bottom: -80px;
  left: -100px;
  animation: float-2 14s ease-in-out infinite;
}

@keyframes float-1 {
  0%,
  100% {
    transform: translate(0, 0);
  }
  50% {
    transform: translate(-20px, 20px);
  }
}

@keyframes float-2 {
  0%,
  100% {
    transform: translate(0, 0);
  }
  50% {
    transform: translate(20px, -20px);
  }
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
 * Usage
 * ============================================================= */
.usage {
  max-width: 1200px;
  margin: 0 auto;
  padding: clamp(60px, 10vw, 100px) 24px;
}






.faq {
  max-width: 720px;
  margin: 0 auto;
}







@media (max-width: 900px) {
  .usage-steps {
    grid-template-columns: 1fr;
  }
}

/* =============================================================
 * Element Plus overrides  -  把 el-tag / el-step / el-collapse 拉到项目 token
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

/* el-step wrapper */
.usage-steps {
  margin: 0 auto 64px;
  padding: 0;
  list-style: none;
  max-width: 720px;
}

.usage-steps :deep(.el-step__title) {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.015em;
  color: var(--color-text);}

.usage-steps :deep(.el-step__description) {
  font-size: 14px;
  color: var(--color-text-muted);
  line-height: 1.6;
  padding-right: 12px;
}

.usage-steps :deep(.el-step__head.is-finish),
.usage-steps :deep(.el-step__head.is-process) {
  color: var(--color-accent);
  border-color: var(--color-accent);
}

.usage-steps :deep(.el-step__head.is-wait) {
  color: var(--color-text-soft);
  border-color: var(--color-border);
}

/* FAQ */
.faq {
  max-width: 720px;
  margin: 0 auto;
}

.faq-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(22px, 2.6vw, 28px);
  font-weight: 700;
  letter-spacing: -0.025em;
  margin: 0 0 24px;
  text-align: center;
  color: var(--color-text);
}

.faq-answer {
  font-size: 14px;
  line-height: 1.65;
  color: var(--color-text-muted);
  margin: 0;
}

.faq :deep(.el-collapse-item__header) {
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  border-bottom: 1px solid var(--color-border);
}

.faq :deep(.el-collapse-item__content) {
  padding-bottom: 16px;
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
  display: inline-block;
  align-self: flex-start;
  padding: 4px 10px;
  background: rgba(76, 175, 80, 0.12);
  color: var(--color-accent);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  border-radius: 999px;
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
 * Join Us  -  邀请贡献者
 * 3 个分类(数字 + 标题 + 描述)+ CTA 区域(斜体引言 + 大按钮)
 * ============================================================= */
.join-list {
  list-style: none;
  padding: 0;
  margin: 48px 0 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.join-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  padding: 32px 28px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  transition: transform 250ms ease, border-color 250ms ease;
}

.join-item:hover {
  transform: translateY(-4px);
  border-color: var(--color-accent);
}

.join-num {
  display: inline-block;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 36px;
  font-weight: 700;
  color: var(--color-accent);
  letter-spacing: -0.02em;
  line-height: 1;
  margin-bottom: 20px;}

.join-item-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.015em;
  margin: 0 0 10px;
  color: var(--color-text);}

.join-item-desc {
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-muted);
  margin: 0;
}

/* CTA 区域  -  斜体引言 + 绿色实心大按钮 */
.join-cta {
  margin-top: 64px;
  padding: 56px 32px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 20px;
  text-align: center;
}

.join-cta-text {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(20px, 2.5vw, 28px);
  font-weight: 400;
  font-style: italic;
  line-height: 1.5;
  letter-spacing: -0.01em;
  color: var(--color-text);
  margin: 0 auto 32px;
  max-width: 720px;
}

.join-cta-btn {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 16px 36px;
  background: var(--color-accent);
  color: #fff;
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 17px;
  font-weight: 600;
  text-decoration: none;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  transition: background 250ms ease, transform 250ms ease, box-shadow 250ms ease;
}

.join-cta-btn:hover {
  background: var(--color-accent-hover);
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(76, 175, 80, 0.35);
}

.join-cta-btn .cta-arrow {
  font-size: 18px;
  transition: transform 250ms ease;
}

.join-cta-btn:hover .cta-arrow {
  transform: translateX(3px);
}

/* =============================================================
 * Sponsors  -  项目赞助者(目前骨架屏占位,未接 GitHub API)
 * 6 个圆形头像 + 名字条,flex 居中,gap 20px
 * ============================================================= */
.sponsors {
  margin-top: 64px;
  text-align: center;
}

.sponsors-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(22px, 2.6vw, 28px);
  font-weight: 700;
  letter-spacing: -0.025em;
  margin: 0 0 8px;
  color: var(--color-text);
}

.sponsors-subtitle {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0 0 32px;
}

.sponsors-grid {
  list-style: none;
  padding: 0;
  margin: 0 auto;
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 24px 20px;
  max-width: 720px;
}

.sponsor-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: 88px;
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

.sponsors-note {
  margin: 32px auto 0;
  max-width: 480px;
  text-align: center;
  font-size: 12px;
  color: var(--color-text-soft);
  font-style: italic;
}

@media (max-width: 900px) {
  .join-list {
    grid-template-columns: 1fr;
  }
  .join-cta {
    padding: 36px 24px;
  }
  .sponsors-grid {
    gap: 20px 16px;
  }
  .sponsor-card {
    width: 72px;
  }
  .skeleton-circle {
    width: 48px;
    height: 48px;
  }
}

/* =============================================================
 * Contact
 * ============================================================= */
.contact {
  max-width: 1200px;
  margin: 0 auto;
  padding: clamp(60px, 10vw, 100px) 24px;
}

.contact-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.contact-card {
  padding: 28px 24px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  text-decoration: none;
  color: inherit;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: transform 200ms ease, border-color 200ms ease;
}

a.contact-card:hover {
  transform: translateY(-2px);
  border-color: var(--color-accent);
}

.contact-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-text-soft);
}

.contact-value {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 18px;
  color: var(--color-text);
}

@media (max-width: 768px) {
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