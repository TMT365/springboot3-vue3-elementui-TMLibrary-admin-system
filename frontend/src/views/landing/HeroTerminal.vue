<script setup lang="ts">
/**
 * HeroTerminal  -  首页 Hero 上的终端演示窗
 *
 * <h2>它解决什么问题</h2>
 * <p>Landing 讲了半天"这是什么",但没回答"我怎么跑起来"。这个终端把
 * 四步上手流程循环演示一遍,放在 Hero 右下角的空位上 —— 访客第一屏
 * 就知道这东西是真的能跑的,而且知道怎么跑。</p>
 *
 * <h2>为什么是"滚动累积"而不是"每轮清屏"</h2>
 * <p>第一版每走完一步就把整屏清掉重来。看着很假:真实终端不会自己
 * <code>clear</code>,而且每次清屏都是一次全量 DOM 重建,
 * 视觉上"啪"地闪一下。</p>
 * <p>现在改成真终端的行为:命令行和输出都<b>追加</b>到同一个列表里,
 * 老内容从顶部推出可视区。组件实例只创建一次,列表项靠 <code>:key</code>
 * 复用,不再整块重建 —— 这才是"流畅"的来源。</p>
 *
 * <h2>位置为什么不会再动了</h2>
 * <p>累积的代价是内容会无限长。所以做了三件事把窗口锁死:</p>
 * <ol>
 *   <li><b>固定高度</b> —— <code>.term-body</code> 用 <code>height</code>
 *       而不是 <code>min-height</code>,内容再多也不撑高</li>
 *   <li><b>底部对齐</b> —— flex 列 + <code>justify-content: flex-end</code>,
 *       溢出的是顶部(旧内容),最新的一行永远可见。
 *       这正是终端"滚到底"的状态</li>
 *   <li><b>截断</b> —— 只保留最近 {@link MAX_LINES} 行。
 *       可视区只放得下 6 行左右,留 14 行缓冲意味着截断发生在看不见的地方,
 *       DOM 也永远有界(不然跑一晚上就是几千个节点)</li>
 * </ol>
 *
 * <h2>为什么不沿用"CSS width + steps()"那套打字动画</h2>
 * <p>原始写法是给 <code>.text</code> 一个固定 <code>width: 6.2em</code>,
 * 再用 <code>steps(11)</code> 走 <code>width</code>。一是宽度写死(6.2em 是
 * 照着 "Loading..." 11 个字符量的,换字长就露馅),二是只能打一句
 * (步数得逐条手算)。所以改成 JS 逐字符驱动,增删步骤不用改样式。</p>
 *
 * <h2>打字速度:按"总时长恒定"算,不是"每字符恒定"</h2>
 * <p>每字符固定 30ms 的话,89 字符的 git clone 要敲 2.7 秒,44 字符的
 * <code>npm run dev</code> 只要 1.3 秒 —— 节奏忽快忽慢。这里反过来:
 * 每条命令的目标总时长固定 ≈1.3 秒,再按长度反推每字符延迟(并 clamp 住
 * 上下限),长命令快速带过、短命令从容敲完,整体节奏是稳的。</p>
 *
 * <h2>无障碍</h2>
 * <ul>
 *   <li><b>aria-hidden</b> —— 它是插图,不是导航。真正的上手说明在
 *       README(「项目结构」区块有直达链接),读屏用户不该在这里
 *       听一遍终端打字流水账。</li>
 *   <li><b>prefers-reduced-motion 必须用 JS 判断</b> —— theme.css 第 4 节
 *       那条全局规则是靠 <code>!important</code> 压 CSS
 *       <code>animation/transition</code> 时长的,而这里的打字是
 *       <code>setTimeout</code> 驱动的,<b>CSS 管不着</b>。
 *       所以必须显式 matchMedia 一次,命中就退化成静态帧。</li>
 *   <li><b>离屏暂停</b> —— 用户往下滚之后 Hero 看不见了,定时器还在跑
 *       就是白烧 CPU。用 IntersectionObserver 在离开视口时停掉。</li>
 * </ul>
 */
import { onBeforeUnmount, onMounted, ref } from 'vue'

/** 输出行的语义类型 —— 决定颜色 */
type OutKind = 'dim' | 'ok' | 'note'
/** 列表里一行的类型:'cmd' 是命令行(带 $ 提示符),其余是输出 */
type LineKind = 'cmd' | OutKind

interface OutLine {
  text: string
  kind?: OutKind
}

interface TermStep {
  /** 敲进去的命令 */
  cmd: string
  out?: OutLine[]
}

interface TermLine {
  id: number
  kind: LineKind
  text: string
}

/**
 * 四步上手流程。命令全部对照仓库核实过,不是编的:
 *   - schema.sql 的用法来自它自己的文件头注释
 *   - dev.sh / .env.example 见 TMLibrary/scripts/dev.sh
 *   - 端口:后端 8080(application.yml 没写 server.port → Spring Boot 默认),
 *           前端 5173(Vite 默认)
 */
const STEPS: TermStep[] = [
  {
    cmd: 'git clone https://github.com/TMT365/springboot3-vue3-elementui-TMLibrary-admin-system.git',
    out: [
      { text: "Cloning into 'springboot3-vue3-elementui-…'...", kind: 'dim' },
      { text: 'done.', kind: 'dim' },
    ],
  },
  {
    cmd: 'mysql -uroot -p < TMLibrary/scripts/schema.sql',
    out: [{ text: '✓ 建库建表完成 · tmlibrary', kind: 'ok' }],
  },
  {
    cmd: 'cd TMLibrary && cp .env.example .env && ./scripts/dev.sh',
    out: [
      { text: '# 先把 DB_PASSWORD 填进 .env', kind: 'note' },
      { text: '✓ Tomcat started on port 8080 · dev', kind: 'ok' },
    ],
  },
  {
    cmd: 'cd ../frontend && npm install && npm run dev',
    out: [{ text: '✓ Local: http://localhost:5173/', kind: 'ok' }],
  },
]

/** 每条命令的目标打字总时长(ms)—— 见组件头注释 */
const TYPE_DURATION = 1300
const CHAR_DELAY_MIN = 14
const CHAR_DELAY_MAX = 46

/** 输出逐行出现的间隔 / 一步结束后停多久 / 两步之间的空隙 / 首屏延迟 */
const OUT_LINE_DELAY = 240
const HOLD_MS = 1500
const NEXT_STEP_DELAY = 300
const INITIAL_DELAY = 500

/**
 * 保留的行数上限。
 * 可视区现在有 12 行,所以这个值必须明显大于 12 ——
 * 截断点一旦落进可视区,顶部会出现"内容被抽走"的突兀感。
 * 24 留出约一屏的缓冲,DOM 成本可以忽略。
 */
const MAX_LINES = 24

/** 已提交的历史行(命令行 + 输出) */
const lines = ref<TermLine[]>([])
/** 正在敲的那一行(还没回车) */
const current = ref('')
/** 本步已吐出的输出行数 */
const outIndex = ref(0)
/** 自增 id —— 给 v-for 做 key,保证列表项被复用而不是重建 */
let seq = 0

const stepIndex = ref(0)
const reducedMotion = ref(false)

let timer: ReturnType<typeof setTimeout> | null = null

function clearTimer(): void {
  if (timer !== null) {
    clearTimeout(timer)
    timer = null
  }
}

function schedule(fn: () => void, ms: number): void {
  clearTimer()
  timer = setTimeout(fn, ms)
}

/** 追加一行,并顺手把超出上限的老行截掉 */
function push(kind: LineKind, text: string): void {
  lines.value.push({ id: seq++, kind, text })
  if (lines.value.length > MAX_LINES) {
    // splice 而不是整体替换:Vue 3 的响应式数组是 Proxy,
    // splice 只会触发被改动区间的更新,比 [...slice(-N)] 的全量替换省
    lines.value.splice(0, lines.value.length - MAX_LINES)
  }
}

/** 敲下一个字符 */
function typeNext(): void {
  const cmd = STEPS[stepIndex.value].cmd
  // 用 code point 而不是 UTF-16 单元切,免得把 emoji / 代理对劈成半个字符
  const chars = [...cmd]
  if ([...current.value].length >= chars.length) {
    // 回车:这一行定格成历史,开始吐输出
    push('cmd', cmd)
    current.value = ''
    revealOut()
    return
  }
  current.value = chars.slice(0, [...current.value].length + 1).join('')

  const perChar = Math.min(
    CHAR_DELAY_MAX,
    Math.max(CHAR_DELAY_MIN, TYPE_DURATION / Math.max(chars.length, 1)),
  )
  // ±30% 抖动 —— 匀速打字像机器,带点抖动才像人在敲
  schedule(typeNext, perChar * (0.7 + Math.random() * 0.6))
}

/** 一行一行往外吐输出;输出是追加在命令行**下面**的 */
function revealOut(): void {
  const out = STEPS[stepIndex.value].out ?? []
  if (outIndex.value >= out.length) {
    // 本步结束 —— 停一下让最后一行被读到,然后接下一步(不清屏)
    schedule(advance, HOLD_MS)
    return
  }
  const line = out[outIndex.value]
  push(line.kind ?? 'dim', line.text)
  outIndex.value += 1
  schedule(revealOut, OUT_LINE_DELAY)
}

/**
 * 进入下一步。
 * <b>注意这里不清空 lines</b> —— 上一轮的痕迹留在屏幕上,新命令接着往下写,
 * 这才是终端的样子,也是"流畅"的来源。
 */
function advance(): void {
  stepIndex.value = (stepIndex.value + 1) % STEPS.length
  outIndex.value = 0
  current.value = ''
  schedule(typeNext, NEXT_STEP_DELAY)
}

/**
 * 静态帧:把整段流程一次性铺出来,尾部对齐。
 * 给 prefers-reduced-motion 用 —— 不打字,但内容完整可读,
 * 而且直接停在"跑起来了"的结局上,信息量比停在第 0 步大。
 */
function renderStatic(): void {
  clearTimer()
  lines.value = []
  seq = 0
  for (const s of STEPS) {
    push('cmd', s.cmd)
    for (const o of s.out ?? []) push(o.kind ?? 'dim', o.text)
  }
  current.value = ''
}

/* ---------------- 生命周期 ---------------- */

let observer: IntersectionObserver | null = null
const rootRef = ref<HTMLElement | null>(null)
/** 是否在视口里 —— 离屏时不该继续烧定时器 */
let onScreen = true

function start(): void {
  if (reducedMotion.value) return
  schedule(typeNext, INITIAL_DELAY)
}

onMounted(() => {
  // 必须用 JS 判断:全局的 prefers-reduced-motion 规则只压得住 CSS 动画,
  // 压不住 setTimeout(见组件头注释)
  reducedMotion.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches

  if (reducedMotion.value) {
    renderStatic()
    return
  }

  observer = new IntersectionObserver(
    (entries) => {
      const visible = entries[0]?.isIntersecting ?? true
      if (visible === onScreen) return
      onScreen = visible
      if (visible) start()
      else clearTimer()
    },
    { threshold: 0 },
  )
  if (rootRef.value) observer.observe(rootRef.value)

  start()
})

onBeforeUnmount(() => {
  clearTimer()
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <!-- aria-hidden:插图,不是内容。真正的上手说明在 README -->
  <div ref="rootRef" class="term" aria-hidden="true">
    <div class="term-bar">
      <span class="term-dots">
        <i class="term-dot is-close" />
        <i class="term-dot is-min" />
        <i class="term-dot is-max" />
      </span>
      <span class="term-title">tmlibrary — bash</span>
    </div>

    <!--
      滚动区。历史行 + 底部一条"活动行"。
      活动行永远在最后:$ + 正在敲的内容 + 光标。
      命令回车后 current 清空,它就退化成"等待输入的提示符"($ ▊),
      输出行则追加到它上面 —— 和真终端一致。
    -->
    <div class="term-body">
      <div
        v-for="line in lines"
        :key="line.id"
        class="term-line"
        :class="line.kind === 'cmd' ? 'is-cmd' : `is-${line.kind}`"
      >
        <span v-if="line.kind === 'cmd'" class="term-prompt">$</span>
        <span class="term-text">{{ line.text }}</span>
      </div>

      <div class="term-line is-cmd is-live">
        <span class="term-prompt">$</span>
        <span class="term-text">{{ current }}</span>
        <span class="term-caret" />
      </div>
    </div>
  </div>
</template>

<style scoped>
/* =============================================================
 * 配色
 *
 * 终端**永远是深色**,不跟页面主题走 —— 它演的是一台设备/一个窗口,
 * 亮色主题下把终端刷成米白反而假。
 *
 * 但色值不是随便挑的,全部取自项目调色板:
 *   中性色  → --color-footer-bg(#212121)那一族,和页脚同一个灰
 *   绿      → --color-accent 的暗色档 #66bb6a
 *   青/琥珀 → theme.css §1.5 辅助色相的暗色档
 * 这样它虽然是"另一个世界",但和页面是同一套色。
 * ============================================================= */
.term {
  --t-bg: #161616;
  --t-bar: #242424;
  --t-border: #333333;
  --t-text: #e6e6e6;
  --t-dim: #9a9a9a;
  /* 4.90:1 —— 原来是 #7a7a7a(4.22:1),低于 WCAG 正文 4.5:1 的要求。
     它是"Cloning into … done."这类次要输出的颜色,虽然次要但仍是正文,
     不能按装饰处理,所以提亮到达标 */
  --t-faint: #858585;
  --t-green: #66bb6a;
  --t-amber: #e0a83c;

  /* 行高只在这里定义一次:窗口高度是按"行数 × 行高"算出来的,
     两处各写一个数字迟早会对不上。
     1.75 → 1.65:加到 9 行之后,按 1.75 算窗口会高 48px,
     在 Hero 里那点垂直余量下太奢侈。终端本来就该行距紧凑,
     1.65 视觉上仍然透气 */
  --t-line-height: 1.65;
  /* 可视行数 —— 改这个数字,窗口高度自动跟着变。
     注意:加行数 = 加高度,而 Hero 里留给它的垂直空间是有限的,
     动这个值要连带复核 Landing.vue 里 .hero-terminal 的 bottom
     (那边有完整的垂直预算推导) */
  --t-visible-lines: 12;

  width: min(440px, 100%);
  background: var(--t-bg);
  border: 1px solid var(--t-border);
  border-radius: 10px;
  overflow: hidden;
  text-align: left;
  /* 和其它卡片同一套抬升语言,免得像贴上去的 */
  box-shadow:
    0 2px 6px rgba(17, 25, 40, 0.18),
    0 18px 44px rgba(17, 25, 40, 0.26);
}

/* ---------- 标题栏 ---------- */
.term-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 30px;
  padding: 0 12px;
  background: var(--t-bar);
  border-bottom: 1px solid var(--t-border);
}

.term-dots {
  display: inline-flex;
  gap: 6px;
  flex-shrink: 0;
}

/* 用 macOS 的真实交通灯配色 —— 这三个圆点是"这是终端窗口"最快的识别符号,
   换成项目绿就认不出来了。9px 的小面积,不参与整体配色判断 */
.term-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
}

.term-dot.is-close {
  background: #ff5f57;
}

.term-dot.is-min {
  background: #febc2e;
}

.term-dot.is-max {
  background: #28c840;
}

.term-title {
  font-family: var(--font-mono);
  font-size: 11px;
  color: #a8a8a8;
  /* 窄屏时标题先被裁掉,别把三个圆点挤走 */
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* =============================================================
 * 正文 —— 滚动区
 *
 * height 而不是 min-height:内容累积时窗口不许被撑高,
 * 否则整块的位置会随着内容长度变化而移动。
 *
 * justify-content: flex-end 是"终端滚到底"的关键:内容溢出时
 * 溢出的方向是**顶部**(旧内容),最新一行永远贴着底边可见。
 * 配合 .term-line 的 flex-shrink: 0,行不会被压扁。
 * ============================================================= */
.term-body {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  height: calc(var(--t-visible-lines) * var(--t-line-height) * 1em);
  padding: 14px 16px 16px;
  font-family: var(--font-mono);
  /* 13px:窗口放大之后 12px 显得太空,而且一行能塞 52 个字符,
     89 字符的 git clone 正好折成 2 行 */
  font-size: 13px;
  line-height: var(--t-line-height);
  /* 顶部溢出的旧行在这里被裁掉 */
  overflow: hidden;
}

.term-line {
  display: flex;
  align-items: baseline;
  gap: 6px;
  /* 必须 0:flex 列里溢出的行默认会被压缩,那会让文字挤在一起 */
  flex-shrink: 0;
  /* 长命令要折行(真实终端也会折),但不许把行盒撑破 */
  flex-wrap: wrap;
}

.term-prompt {
  flex-shrink: 0;
  color: var(--t-green);
  font-weight: 600;
  user-select: none;
}

.term-text {
  /* anywhere:git clone 那串 URL 中间没有空格,break-word 对它无效 */
  overflow-wrap: anywhere;
}

/* ---------- 各类型的行 ----------
 *
 * 这四条选择器**故意写成同一个特异度**(0,3,0),靠源码顺序决定胜负。
 * 起因:原来 dim 那条写成 `.is-dim .term-text`(只有 0,2,0),
 * 而"输出行默认色"那条是 `.term-line:not(.is-cmd) .term-text`(0,3,0)——
 * 后者反而赢,于是 dim 行拿到的还是默认色,`--t-faint` 那条成了死代码,
 * 次要输出的降级效果和对比度修正都不会生效。
 * 现在每条都带 .term-line,特异度拉平,谁在后面谁生效。
 */
.is-cmd .term-text {
  color: var(--t-text);
}

/* 输出行:缩进到提示符右侧,和命令正文对齐,默认次要色 */
.term-line:not(.is-cmd) .term-text {
  padding-left: calc(1ch + 6px);
  color: var(--t-dim);
}

/* dim:再压一档(比普通输出更淡) */
.term-line.is-dim .term-text {
  color: var(--t-faint);
}

.term-line.is-ok .term-text {
  color: var(--t-green);
}

/* 注释行(以 # 开头的那种) */
.term-line.is-note .term-text {
  color: var(--t-amber);
  opacity: 0.85;
}

/* ---------- 光标 ---------- */
.term-caret {
  display: inline-block;
  flex-shrink: 0;
  width: 0.55em;
  height: 1.05em;
  background: var(--t-green);
  /* 用 translateY 对齐基线,比 vertical-align 稳 */
  transform: translateY(0.16em);
  animation: term-blink 1.06s step-end infinite;
}

@keyframes term-blink {
  0%,
  50% {
    opacity: 1;
  }
  50.01%,
  100% {
    opacity: 0;
  }
}
</style>
