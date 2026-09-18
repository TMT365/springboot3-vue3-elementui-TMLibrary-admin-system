<script setup lang="ts">
/**
 * AuthCard  -  登录 / 注册 / 忘记密码 / 重置密码共用的认证页外壳
 *
 * <h2>两套布局,不是一个 CSS 硬撑两种屏幕</h2>
 * 之前的做法:一套"居中窄卡片"的样式,再用 @media (max-width: 600px) 往手机上收一收。
 * 结果 PC 上就是**一张 350px 的卡片孤零零浮在 1920px 的空白中间** —— 那是手机
 * 布局的放大版,不是 PC 布局。窄卡片本身没错(表单就该窄),错的是把整屏都让给了空白。
 *
 * 现在按屏宽分成两套完整布局:
 *
 *   ≥901px  PC 左右分栏
 *     ├─ 左:书架插画 + 文案(纯 CSS 画的书脊 / 浮尘 / 光晕,零图片依赖)
 *     └─ 右:表单卡片(仍然是 380px 的窄卡片 —— 输入框不该跟着屏幕变宽)
 *     左半屏是**装饰**,aria-hidden,`pointer-events:none`,键盘和读屏都够不着它。
 *
 *   ≤900px  平板 / 手机单列
 *     左半屏整个 display:none,回到原来那张居中卡片。≤600px 的手感与改动前一致。
 *
 * 为什么这样分而不是"手机优先":
 *   表单卡片的尺寸(44px 输入框 / 20px 圆角 / 350px 宽)本来就是照手机的拇指
 *   和窄屏定的,直接当基线最省事;PC 的差异在于**多了一整块空间要处理**,
 *   那是"加一层",写成 min-width 的增强比写成 max-width 的降级更好读。
 *
 * <h2>插槽</h2>
 *   default  -  表单主体(各页自己的 el-form,校验 / rules 留各页不动)
 *   footer   -  卡片底部那一行(注册页的「已有账号?去登录」)
 *
 * <h2>主题</h2>
 *   所有颜色走 var(--color-*),亮 / 暗两套自动成立。
 *   左半屏的书脊配色另有一组 --bk-1..9(见样式第 3 节),暗色单独覆盖 ——
 *   深底上那套低饱和的书脊色会糊成一团,必须整体提亮一档。
 *
 * <h2>社交登录</h2>
 *   Google / Apple / Twitter 三个圆形按钮**保留但整体置灰** ——
 *   后端还没接第三方登录,做成能点会误导用户。
 *   注意 disabled 的原生按钮**不派发 click 事件**(规范如此,也不冒泡),
 *   所以点击提示挂在外层 .social-item 上,按钮再用 pointer-events:none
 *   把命中让给外层 —— 这样「禁用」和「点了给提示」才能同时成立。
 */

interface Props {
  /** 卡片主标题 */
  title: string
  /** 副标题,不传则不渲染 */
  subtitle?: string
  /** 是否显示第三方登录那一行 */
  showSocial?: boolean
}

withDefaults(defineProps<Props>(), {
  subtitle: '',
  showSocial: true,
})

/* =============================================================
 * 左半屏文案(仅 PC 可见)
 *
 * 只讲这个系统**真实有的**三件事:图书 / 订单 / 权限。不写"高效便捷"这类
 * 放哪儿都成立的话 —— 访客在登录页看到的每一句都该是能被产品兑现的。
 * ============================================================= */

interface VisualPoint {
  /** 全局注册的 Element Plus 图标名(见 main.ts 的注册循环) */
  icon: string
  /** 加粗的那半句 */
  strong: string
  /** 后面跟的解释 */
  rest: string
}

const POINTS: VisualPoint[] = [
  { icon: 'Reading', strong: '图书管理', rest: '著录、检索、库存一览' },
  { icon: 'List', strong: '订单流转', rest: '下单锁库存,状态全程可追' },
  { icon: 'UserFilled', strong: '权限分级', rest: 'USER / ADMIN / BOSS 各司其职' },
]

/* =============================================================
 * 书架插画的数据
 *
 * 整幅插画是纯 CSS 画的:每本书就是一个 span,只有宽高 / 颜色 / 动画相位
 * 三个参数,写进数组比在模板里摆九个 <span class="book b1">…<span class="book b9">
 * 好改 —— 想加一本就加一行,想调顺序就换位置,不用同步两处。
 *
 * 颜色引用的是 --bk-* 调色板(定义在第 3 节),而不是写死色值:
 * 那样暗色主题只要覆盖九个变量,书本的"呼吸 / 抽出"动画完全不用动。
 * ============================================================= */

interface Book {
  /** 书脊宽(px)—— 参差不齐才像真书架,统一宽度会变成色卡 */
  w: number
  /** 书脊高(px) */
  h: number
  /** 书脊颜色,取 --bk-* 变量 */
  c: string
  /** 呼吸动画的延迟(s)—— 九本同时起伏会像波浪,错开才像各自在喘气 */
  delay: number
  /** 唯一一本会被"抽出来又放回去"的书 */
  pull?: boolean
  /** 倾斜角度(deg)—— 末位那本斜靠在旁边那本上 */
  rot?: number
}

const BOOKS: Book[] = [
  { w: 20, h: 96, c: 'var(--bk-5)', delay: 0 },
  { w: 26, h: 132, c: 'var(--bk-1)', delay: 0.4 },
  { w: 15, h: 108, c: 'var(--bk-7)', delay: 0.8 },
  { w: 30, h: 150, c: 'var(--bk-3)', delay: 1.2, pull: true },
  { w: 22, h: 122, c: 'var(--bk-6)', delay: 1.6 },
  { w: 17, h: 100, c: 'var(--bk-2)', delay: 2.0 },
  { w: 28, h: 140, c: 'var(--bk-4)', delay: 2.4 },
  { w: 13, h: 86, c: 'var(--bk-8)', delay: 2.8 },
  /* 末位斜靠:transform-origin 定在右下角,负角度 = 往左倒,正好压在邻居身上 */
  { w: 24, h: 128, c: 'var(--bk-9)', delay: 3.2, rot: -8 },
]

interface Mote {
  /** 水平位置(%) */
  x: number
  /** 直径(px) */
  s: number
  /** 起始延迟(s) */
  delay: number
  /** 单次上升耗时(s)—— 各不相同才不会看出"队列" */
  dur: number
}

/** 浮尘:书架上方慢慢升起的绿色小点,给静态插画加一点空气感 */
const MOTES: Mote[] = [
  { x: 10, s: 4, delay: 0, dur: 12 },
  { x: 22, s: 3, delay: 2.6, dur: 14 },
  { x: 38, s: 5, delay: 5.2, dur: 11 },
  { x: 52, s: 3, delay: 1.4, dur: 15 },
  { x: 66, s: 4, delay: 7.8, dur: 13 },
  { x: 80, s: 3, delay: 3.9, dur: 12.5 },
  { x: 92, s: 5, delay: 9.1, dur: 16 },
]

interface SocialItem {
  name: string
  /** 24x24 视野下的品牌 path */
  path: string
}

const SOCIALS: SocialItem[] = [
  {
    name: 'Google',
    path: 'M12.48 10.92v3.28h7.84c-.24 1.84-.853 3.187-1.787 4.133-1.147 1.147-2.933 2.4-6.053 2.4-4.827 0-8.6-3.893-8.6-8.72s3.773-8.72 8.6-8.72c2.6 0 4.507 1.027 5.907 2.347l2.307-2.307C18.747 1.44 16.133 0 12.48 0 5.867 0 .307 5.387.307 12s5.56 12 12.173 12c3.573 0 6.267-1.173 8.373-3.36 2.16-2.16 2.84-5.213 2.84-7.667 0-.76-.053-1.467-.173-2.053H12.48z',
  },
  {
    name: 'Apple',
    path: 'M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701',
  },
  {
    name: 'Twitter',
    path: 'M23.953 4.57a10 10 0 0 1-2.825.775 4.958 4.958 0 0 0 2.163-2.723c-.951.555-2.005.959-3.127 1.184a4.92 4.92 0 0 0-8.384 4.482C7.69 8.095 4.067 6.13 1.64 3.162a4.822 4.822 0 0 0-.666 2.475c0 1.71.87 3.213 2.188 4.096a4.904 4.904 0 0 1-2.228-.616v.06a4.923 4.923 0 0 0 3.946 4.827 4.996 4.996 0 0 1-2.212.085 4.936 4.936 0 0 0 4.604 3.417 9.867 9.867 0 0 1-6.102 2.105c-.39 0-.779-.023-1.17-.067a13.995 13.995 0 0 0 7.557 2.209c9.053 0 13.998-7.496 13.998-13.985 0-.21 0-.42-.015-.63A9.935 9.935 0 0 0 24 4.59z',
  },
]

function onSocialClick(): void {
  ElMessage.info('第三方登录暂未开放')
}
</script>

<template>
  <div class="auth-page">
    <!--
      返回首页。放在认证页共用的外壳里 —— 四个页面(登录 / 注册 / 忘记密码 /
      重置密码)自动都有,不用各写一遍。

      绝对定位在左上角,而不是塞进卡片里:卡片是居中的网格项,
      往里加东西会把 header 往下挤、视觉重心偏掉。而且访客在认证页
      最想做的事之一就是"算了,先回去看看",这个入口该固定待在一个位置。
    -->
    <router-link to="/" class="auth-back">
      <span class="auth-back-arrow" aria-hidden="true">←</span>
      返回首页
    </router-link>

    <!-- 装饰光晕:单独一层,自己 overflow:hidden -->
    <div class="auth-deco" aria-hidden="true">
      <span class="deco-circle deco-circle-1" />
      <span class="deco-circle deco-circle-2" />
    </div>

    <div class="auth-split">
      <!--
        ============ 左半屏(仅 PC) ============
        纯装饰:aria-hidden 让读屏跳过,pointer-events:none 让它不参与命中测试
        (整块摸上去"不存在"),里面没有任何可聚焦元素。
        手机 / 平板上 display:none —— 见样式第 10 节。
      -->
      <aside class="auth-visual" aria-hidden="true">
        <div class="visual-copy">
          <p class="visual-kicker">TMLibrary</p>

          <h2 class="visual-title">
            让每一本书<br />
            都<em>各得其所</em>
          </h2>

          <p class="visual-lede">
            从图书著录、检索到借阅与订单流转,一套界面把馆藏、读者和每一笔交易都照看到。
          </p>

          <ul class="visual-points">
            <li v-for="p in POINTS" :key="p.strong">
              <span class="point-icon">
                <el-icon><component :is="p.icon" /></el-icon>
              </span>
              <span class="point-text">
                <b>{{ p.strong }}</b>
                <span class="point-sep" aria-hidden="true">·</span>{{ p.rest }}
              </span>
            </li>
          </ul>
        </div>

        <!-- 书架插画:光晕 + 浮尘 + 九本书 + 底板,全部 CSS 画出来 -->
        <div class="visual-stage">
          <span class="stage-halo" />

          <span
            v-for="(m, i) in MOTES"
            :key="`mote-${i}`"
            class="mote"
            :style="{
              '--x': `${m.x}%`,
              '--s': `${m.s}px`,
              '--d': `${m.delay}s`,
              '--t': `${m.dur}s`,
            }"
          />

          <div class="shelf">
            <div class="shelf-books">
              <span
                v-for="(b, i) in BOOKS"
                :key="`book-${i}`"
                class="book"
                :class="{ 'is-pulled': b.pull }"
                :style="{
                  '--w': `${b.w}px`,
                  '--h': `${b.h}px`,
                  '--c': b.c,
                  '--rot': `${b.rot ?? 0}deg`,
                  '--d': `${b.delay}s`,
                }"
              />
            </div>
            <div class="shelf-board" />
          </div>

          <p class="visual-quote">「把每一本书,放到它该在的位置。」</p>
        </div>
      </aside>

      <!-- ============ 右半屏:表单卡片 ============ -->
      <section class="auth-card">
        <header class="auth-head">
          <span class="auth-logo">T</span>
          <h1 class="auth-title">{{ title }}</h1>
          <p v-if="subtitle" class="auth-subtitle">{{ subtitle }}</p>
        </header>

        <div class="auth-body">
          <slot />
        </div>

        <template v-if="showSocial">
          <div class="auth-divider"><span>或</span></div>

          <div class="auth-social">
            <span
              v-for="s in SOCIALS"
              :key="s.name"
              class="social-item"
              title="暂未开放"
              @click="onSocialClick"
            >
              <button
                type="button"
                class="social-btn"
                disabled
                title="暂未开放"
                :aria-label="`${s.name} 登录(暂未开放)`"
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path :d="s.path" />
                </svg>
              </button>
            </span>
          </div>
        </template>

        <div v-if="$slots.footer" class="auth-foot">
          <slot name="footer" />
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* =============================================================
 * 1. 页面底(两套布局共用)
 * ============================================================= */
.auth-page {
  position: relative;
  min-height: 100vh;
  /* 手机上 100vh 含被地址栏遮掉的那段,卡片会被顶出可视区;
     dvh 支持时用真实可视高度(上面一行是给老浏览器的兜底) */
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 32px 20px;
  background: var(--color-bg);
  color: var(--color-text);
}

/* 光晕层固定铺满视口,overflow 收在自己身上 ——
 * 不能给 .auth-page 加 overflow:hidden:注册页卡片比一屏高,
 * 页面要能纵向滚,裁了卡片下半截就点不到提交按钮了。 */
.auth-deco {
  position: fixed;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(76, 175, 80, 0.14) 0%,
    transparent 70%
  );
}

/* 暗色下accent 变浅(#66bb6a),同一份 rgba 在深底上会更扎眼,压一档 */
:root[data-theme='dark'] .auth-page .deco-circle {
  background: radial-gradient(
    circle,
    rgba(102, 187, 106, 0.10) 0%,
    transparent 70%
  );
}

.deco-circle-1 {
  width: 460px;
  height: 460px;
  top: -170px;
  right: -170px;
}

.deco-circle-2 {
  width: 360px;
  height: 360px;
  bottom: -130px;
  left: -140px;
}

/* =============================================================
 * 返回首页  -  认证页左上角的固定出口
 *
 * 视觉上刻意比卡片弱一档(描边药丸 + 次要文字色),但 hover 时
 * 描边和文字一起转 accent —— 它是个"退路",不该和主按钮抢注意力,
 * 但真要用的时候得一眼找得到。
 * 颜色全走 token,暗色主题自动跟随。
 * ============================================================= */
.auth-back {
  position: absolute;
  top: 24px;
  left: 24px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-muted);
  text-decoration: none;
  background: var(--color-card);
  box-shadow:
    0 0 0 1px var(--color-border),
    var(--shadow-card);
  transition:
    color 200ms ease,
    box-shadow 200ms ease,
    transform 200ms cubic-bezier(0.22, 1, 0.36, 1);
}

.auth-back:hover {
  color: var(--color-accent);
  transform: translateX(-2px);
  box-shadow:
    0 0 0 1px var(--color-accent),
    var(--shadow-card);
}

.auth-back:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}

/* 箭头单独包一层:hover 时往左挪,强化"返回"的方向感 */
.auth-back-arrow {
  transition: transform 200ms cubic-bezier(0.22, 1, 0.36, 1);
}

.auth-back:hover .auth-back-arrow {
  transform: translateX(-2px);
}

/* =============================================================
 * 2. 分栏容器
 *
 * 两列的宽度比是 1.05 : 0.95 —— 左半屏略宽。左边是"看"的(插画 + 文案),
 * 右边是"用"的(窄卡片),宽度一样会显得右边空、左边挤。
 * 列间距 clamp(32px, 4vw, 72px):窄一点的 PC 上不至于把两列挤到一起,
 * 宽屏上也不会散成两个孤岛。
 * ============================================================= */
.auth-split {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 1180px;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  align-items: center;
  gap: clamp(32px, 4vw, 72px);
}

/* =============================================================
 * 3. 左半屏  -  文案 + 书架插画(仅 PC,≤900px 整块隐藏)
 * ============================================================= */

/* 书脊调色板。九个色都刻意压低饱和度:这是插画不是图表,颜色一艳
 * 就跟右边的绿色主按钮抢注意力了。亮色这套是在米白底(#faf8f3)上定的,
 * 暗色在文件下方单独覆盖(深底上必须整体提亮,否则糊成一片黑)。 */
.auth-visual {
  --bk-1: #2f6b4f; /* 深松绿 */
  --bk-2: #6aa06f; /* 浅一档的同族绿 */
  --bk-3: #c08a3e; /* 赭黄 */
  --bk-4: #a9583f; /* 陶土红 */
  --bk-5: #3d5f7d; /* 黛蓝 */
  --bk-6: #7c6a94; /* 藕紫 */
  --bk-7: #8b8f76; /* 灰绿 */
  --bk-8: #d8cdb4; /* 米白(纸) */
  --bk-9: #2b3138; /* 墨 */
  --shelf-top: #c9b28b;
  --shelf-bottom: #a3855c;
  --shelf-edge: #8a6f4b;

  position: relative;
  display: flex;
  flex-direction: column;
  gap: 26px;
  min-height: min(78vh, 660px);
  padding: 8px 0;
  /* 纯装饰,不参与命中测试 —— 鼠标划过去不该有任何反应 */
  pointer-events: none;
}

/* ---------- 文案 ---------- */

.visual-copy {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 眉头小字:一枚 accent 短线 + 全大写字母,editorial 的常规起手式 */
.visual-kicker {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--color-text-soft);
}

.visual-kicker::before {
  content: '';
  width: 28px;
  height: 2px;
  border-radius: 2px;
  background: var(--color-accent);
}

.visual-title {
  margin: 0;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(30px, 3.1vw, 44px);
  font-weight: 400;
  line-height: 1.22;
  letter-spacing: -0.02em;
  color: var(--color-text);
}

/* 斜体 + 主色:整句话里只强调这四个字(DM Serif Display 的 italic 已随
   index.html 的 Google Fonts 一起加载,不会触发额外的字形请求) */
.visual-title em {
  font-style: italic;
  color: var(--color-accent);
}

.visual-lede {
  max-width: 30em;
  margin: 2px 0 0;
  font-size: 14.5px;
  line-height: 1.9;
  color: var(--color-text-muted);
  /* 中文没有词间空格,默认换行会在最后一行留两三个字的"孤字"
     (实测 38 字切成 30 + 8)。balance 让两行长度接近,不支持的浏览器
     忽略这条,退回原来的换行 —— 不影响可读性。 */
  text-wrap: balance;
}

.visual-points {
  display: flex;
  flex-direction: column;
  gap: 11px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}

.visual-points li {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 图标气泡:白底 + 一圈极淡描边,和右边的输入框是同一套"卡片"语言 */
.point-icon {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  font-size: 17px;
  line-height: 1;
  color: var(--color-accent);
  background: var(--color-card);
  box-shadow:
    0 0 0 1px var(--color-border),
    0 3px 10px var(--color-shadow);
}

.point-text {
  font-size: 13.5px;
  color: var(--color-text-muted);
}

.point-text b {
  font-weight: 700;
  color: var(--color-text);
}

.point-sep {
  margin: 0 5px;
  color: var(--color-text-soft);
}

/* ---------- 插画舞台 ---------- */

.visual-stage {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  min-height: 240px;
}

/* 光晕:书架背后一团会呼吸的绿光。position 用 bottom 锚定,
 * 不管左半屏多高,它都贴在书架后面而不是飘在半空 */
.stage-halo {
  position: absolute;
  bottom: -20px;
  left: 50%;
  width: 340px;
  height: 340px;
  margin-left: -170px;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(76, 175, 80, 0.16) 0%,
    transparent 68%
  );
  animation: halo-breathe 9s ease-in-out infinite;
}

/* 浮尘:从书架底部升到上方后淡出,循环。--x/--s/--d/--t 由模板给 */
.mote {
  position: absolute;
  left: var(--x);
  bottom: 44px;
  width: var(--s);
  height: var(--s);
  border-radius: 50%;
  background: var(--color-accent);
  opacity: 0;
  animation: mote-rise var(--t) linear infinite;
  animation-delay: var(--d);
}

.shelf {
  position: relative;
  width: min(100%, 380px);
}

.shelf-books {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 6px;
  /* 168px = 最高那本(150)+ 抽书时抬起的 18px,抬起来不会顶出容器 */
  height: 168px;
  padding: 0 20px;
}

/* ---------- 一本书 ---------- */
.book {
  position: relative;
  flex-shrink: 0;
  width: var(--w);
  height: var(--h);
  border-radius: 3px 3px 2px 2px;
  background: var(--c);
  /* 左侧书脊暗边 + 右侧受光棱边 = 一块色块变成一本有厚度的书 */
  box-shadow:
    inset 1px 0 0 rgba(0, 0, 0, 0.22),
    inset -1px 0 0 rgba(255, 255, 255, 0.18),
    0 6px 12px -6px var(--color-shadow-strong);
  /* 倾斜书的支点定在右下角:往左倒时才像"靠在邻居身上",
     支点在中心的话整本书会悬空转 */
  transform-origin: bottom right;
  animation: book-breathe 7s ease-in-out infinite;
  animation-delay: var(--d);
  /* backwards 是必须的:动画有 0~3.2s 的错峰延迟,不填终态的话
     延迟期间元素是"没有 transform"的 —— 末位那本斜靠的书会先笔直站 3.2 秒,
     然后"啪"地倒下去。backwards 让它在延迟期间就取 0% 的关键帧值。 */
  animation-fill-mode: backwards;
}

/* 书脊上的两道"烫金"压印横条 —— 没有它,九本书就是九个彩色矩形 */
.book::after {
  content: '';
  position: absolute;
  top: 13%;
  left: 14%;
  right: 14%;
  height: 2px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.55);
  /* 第二道靠 box-shadow 画,省一个伪元素 */
  box-shadow: 0 6px 0 rgba(255, 255, 255, 0.28);
}

/* 唯一那本会被抽出来的书:自己的动画覆盖呼吸(不能叠加,
   两条动画都写 transform 会互相覆盖,以后一条生效) */
.book.is-pulled {
  /* 简写会把上面那条 animation-fill-mode 重置掉,这里补回来 ——
     现在这本书的 0% 关键帧等于静止态,补不补都一样,
     但哪天给它也加个 --rot,少了这行就会在延迟结束的瞬间"啪"地转过去 */
  animation: book-pull 9s cubic-bezier(0.22, 1, 0.36, 1) infinite;
  animation-delay: var(--d);
  animation-fill-mode: backwards;
}

@keyframes book-breathe {
  0%,
  100% {
    transform: rotate(var(--rot, 0deg)) translateY(0);
  }
  50% {
    transform: rotate(var(--rot, 0deg)) translateY(-4px);
  }
}

/* 抽出 → 停一会儿 → 放回去 → 长时间静止。
   前 62% 完全不动,是为了让这个动作"偶尔发生"而不是一直在抖。 */
@keyframes book-pull {
  0%,
  62%,
  100% {
    transform: rotate(var(--rot, 0deg)) translateY(0);
  }
  72%,
  86% {
    transform: rotate(var(--rot, 0deg)) translateY(-18px);
  }
}

@keyframes halo-breathe {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.75;
  }
  50% {
    transform: scale(1.12);
    opacity: 1;
  }
}

@keyframes mote-rise {
  0% {
    opacity: 0;
    transform: translateY(0) scale(0.6);
  }
  12% {
    opacity: 0.55;
  }
  70% {
    opacity: 0.3;
  }
  100% {
    opacity: 0;
    transform: translateY(-190px) scale(1);
  }
}

/* ---------- 书架底板 ---------- */
.shelf-board {
  position: relative;
  height: 10px;
  border-radius: 4px;
  background: linear-gradient(
    180deg,
    var(--shelf-top) 0%,
    var(--shelf-bottom) 100%
  );
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 16px 30px -14px var(--color-shadow-strong);
}

/* 板子的前立面(厚度)—— 只有一条 10px 的渐变会像贴纸,加 6px 立面才像块板 */
.shelf-board::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  height: 6px;
  border-radius: 0 0 4px 4px;
  background: var(--shelf-edge);
}

.visual-quote {
  margin: 30px 0 0;
  padding-left: 14px;
  border-left: 2px solid var(--color-accent);
  font-family: 'DM Serif Display', Georgia, serif;
  font-style: italic;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-soft);
}

/* ---------- 左半屏入场 ----------
 * 错开 80ms 依次浮上来。用 animation + both 而不是 transition:
 * 这一块是"进场"不是"交互",transition 还得先有个状态切换去触发它。
 * 全局的 prefers-reduced-motion 规则会把时长压到 0.01ms,直接落到终态。 */
.visual-kicker,
.visual-title,
.visual-lede,
.visual-points,
.visual-stage {
  animation: auth-rise 800ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

.visual-kicker {
  animation-delay: 0.05s;
}

.visual-title {
  animation-delay: 0.13s;
}

.visual-lede {
  animation-delay: 0.21s;
}

.visual-points {
  animation-delay: 0.29s;
}

.visual-stage {
  animation-delay: 0.37s;
}

@keyframes auth-rise {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ---------- 左半屏 · 暗色 ----------
 * 深底上低饱和的书脊色会整体变暗、彼此糊在一起,所以九个色统一提亮一档
 * (明度上去了、饱和度反而压低),木板的暖棕也换成深胡桃色。 */
:root[data-theme='dark'] .auth-visual {
  --bk-1: #5fae86;
  --bk-2: #85c48c;
  --bk-3: #d9a75c;
  --bk-4: #c47a5e;
  --bk-5: #5b86a8;
  --bk-6: #9c88b8;
  --bk-7: #a8ac93;
  --bk-8: #e6dcc6;
  --bk-9: #4a525c;
  --shelf-top: #55483a;
  --shelf-bottom: #3b3126;
  --shelf-edge: #2c241c;
}

:root[data-theme='dark'] .stage-halo {
  background: radial-gradient(
    circle,
    rgba(102, 187, 106, 0.14) 0%,
    transparent 68%
  );
}

/* 暗色下书脊的"受光棱边"要收一档:0.18 的白色高光在深底上会亮成一条银线 */
:root[data-theme='dark'] .book {
  box-shadow:
    inset 1px 0 0 rgba(0, 0, 0, 0.35),
    inset -1px 0 0 rgba(255, 255, 255, 0.10),
    0 6px 14px -6px rgba(0, 0, 0, 0.6);
}

:root[data-theme='dark'] .book::after {
  background: rgba(255, 255, 255, 0.34);
  box-shadow: 0 6px 0 rgba(255, 255, 255, 0.16);
}

/* =============================================================
 * 4. 卡片本体
 *
 * 造型语言(来自设计稿):
 *   40px 大圆角 / 25px 35px 内边距 / max-width 350px
 *   多层柔和外阴影(近景 + 远景大气阴影 = 下坠感)
 * 底色用「卡片色 → 页面底色」的极淡渐变:
 *   亮色 #fff → #faf8f3,暗色 #1f1f1f → #1a1a1a
 * 写死 #fff 的话暗色下会变成一块惨白(暗色最刺眼的坑,见 UserSettings 同样处理)。
 *
 * 宽度刻意**不随屏宽变**:表单是输入控件,一行 380px 已经够宽,
 * 拉到 600px 只会让"用户名"三个字和输入框隔着半个屏幕对望。
 * ============================================================= */
.auth-card {
  position: relative;
  z-index: 1;
  justify-self: center;
  width: 100%;
  max-width: 350px;
  padding: 25px 35px;
  border-radius: 40px;
  background: linear-gradient(180deg, var(--color-card) 0%, var(--color-bg) 100%);
  box-shadow:
    var(--shadow-card),                     /* 近 / 中景:亮暗各自定义 */
    0 30px 70px -18px var(--color-shadow),  /* 远景大气阴影 —— "下坠感" */
    var(--edge-highlight);                  /* 顶部受光棱边 */
  transition: box-shadow 300ms ease;
}

.auth-card:hover {
  box-shadow:
    var(--shadow-card-hover),
    0 44px 90px -22px var(--color-shadow),
    var(--edge-highlight);
}

/* =============================================================
 * 5. 头部
 * ============================================================= */
.auth-head {
  text-align: center;
  margin-bottom: 22px;
}

.auth-logo {
  width: 52px;
  height: 52px;
  margin: 0 auto 12px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: linear-gradient(
    135deg,
    var(--color-accent) 0%,
    var(--color-accent-hover) 100%
  );
  color: #fff;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 26px;
  line-height: 1;
  box-shadow: 0 8px 20px -6px rgba(76, 175, 80, 0.55);
}

.auth-title {
  margin: 0;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 23px;
  font-weight: 400;
  letter-spacing: -0.01em;
  color: var(--color-text);
}

.auth-subtitle {
  margin: 4px 0 0;
  font-size: 11.5px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

/* =============================================================
 * 6. 表单控件  -  作用到插槽内容,页面里不再重复写
 * ============================================================= */

/* 表单项间距 */
.auth-card :deep(.el-form-item) {
  margin-bottom: 14px;
}

/* 标签压暗一档,和输入框里的正文拉开层级 */
.auth-card :deep(.el-form-item__label) {
  color: var(--color-text-muted);
  font-size: 13px;
  font-weight: 500;
}

/* 输入框整体高 44px(胶囊)。改 EP 自己的 --el-input-height 而不是覆盖
 * .el-input__inner 的 height —— EP 的 inner 高度和 line-height 都由这个变量
 * 推导,只改 height 会让 line-height 对不上,文字偏上/偏下。 */
.auth-card :deep(.el-input) {
  --el-input-height: 26px;
}

/* ---------- 输入框:白底 / 无边框 / 圆角 20px ----------
 * 「无边框」在暗色下会让输入框和卡片底糊成一片(#1f1f1f 上再放 #1f1f1f),
 * 所以用一条极低对比的 1px 描边(--color-border) + 一层软阴影定型:
 * 视觉上仍是"没有边框",但暗色下能看出输入区的边界。 */
.auth-card :deep(.el-input__wrapper) {
  position: relative;
  padding: 10px 18px;
  border-radius: 20px;
  background: var(--color-card);
  box-shadow:
    0 0 0 1px var(--color-border),
    0 3px 10px var(--color-shadow);
  transition: box-shadow 220ms ease;
}

.auth-card :deep(.el-input__wrapper:hover) {
  box-shadow:
    0 0 0 1px var(--color-text-soft),
    0 4px 12px var(--color-shadow);
}

/* 聚焦:不加整圈高亮,只留「左右两条 2px accent 竖边」(设计稿的要求) */
.auth-card :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px var(--color-border),
    0 6px 18px var(--color-shadow);
}

/* 两条竖边用伪元素画,不用 inset box-shadow ——
 * inset 阴影会贴着 20px 圆角走,在 40px 高的胶囊上整条都是弧,
 * 看起来是"("和")"而不是两条竖线。伪元素是独立的小圆角矩形,
 * 竖向居中 20px 高,离圆角区足够远,才是真正的竖边。 */
.auth-card :deep(.el-input__wrapper)::before,
.auth-card :deep(.el-input__wrapper)::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 2px;
  height: 20px;
  margin-top: -10px;
  border-radius: 2px;
  background: var(--color-accent);
  opacity: 0;
  transform: scaleY(0.35);
  transition:
    opacity 180ms ease,
    transform 200ms cubic-bezier(0.22, 1, 0.36, 1);
  pointer-events: none;
}

.auth-card :deep(.el-input__wrapper)::before {
  left: 7px;
}

.auth-card :deep(.el-input__wrapper)::after {
  right: 7px;
}

.auth-card :deep(.el-input__wrapper.is-focus)::before,
.auth-card :deep(.el-input__wrapper.is-focus)::after {
  opacity: 1;
  transform: scaleY(1);
}

.auth-card :deep(.el-input__inner) {
  font-size: 14px;
  color: var(--color-text);
}

.auth-card :deep(.el-input__inner::placeholder) {
  color: var(--color-text-soft);
}

/* 报错态:EP 默认的红色描边保留(校验提示不能丢),只把竖边染成同色 */
.auth-card :deep(.el-form-item.is-error .el-input__wrapper) {
  box-shadow:
    0 0 0 1px var(--el-color-danger),
    0 3px 10px var(--color-shadow);
}

.auth-card :deep(.el-form-item.is-error .el-input__wrapper)::before,
.auth-card :deep(.el-form-item.is-error .el-input__wrapper)::after {
  background: var(--el-color-danger);
}

.auth-card :deep(.el-form-item__error) {
  padding-top: 4px;
  font-size: 12px;
}

/* 验证码图那一块也跟上圆角语言(组件本身不动,只从外面调节)。
 * 只给 14px 而不是 20px:图里是验证码字符,圆角太大会把四角切掉,
 * 切到字符就读不出来了。 */
.auth-card :deep(.captcha-img-wrap) {
  border: none;
  border-radius: 14px;
  background: var(--color-bg-alt);
  box-shadow: 0 0 0 1px var(--color-border);
}

/* ---------- 主按钮:渐变底 + 圆角 20px + hover 放大 / active 缩小 ---------- */
.auth-card :deep(.auth-submit) {
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 20px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #fff;
  background: linear-gradient(
    135deg,
    var(--color-accent) 0%,
    var(--color-accent-hover) 100%
  );
  box-shadow: 0 10px 24px -8px rgba(76, 175, 80, 0.65);
  transition:
    transform 200ms cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 200ms ease,
    filter 200ms ease;
}

.auth-card :deep(.auth-submit:hover) {
  transform: scale(1.03);
  filter: brightness(1.05);
  box-shadow: 0 14px 28px -8px rgba(76, 175, 80, 0.7);
}

.auth-card :deep(.auth-submit:active) {
  transform: scale(0.95);
}

/* 表单里那个只包按钮的 form-item 不留底部间距,交给下面分隔线控制 */
.auth-card :deep(.el-form-item:last-of-type) {
  margin-bottom: 0;
}

/* =============================================================
 * 7. 社交登录(保留但置灰)
 * ============================================================= */
.auth-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 18px 0 14px;
  font-size: 12px;
  color: var(--color-text-soft);
}

.auth-divider::before,
.auth-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--color-border);
}

.auth-social {
  display: flex;
  justify-content: center;
  gap: 14px;
  /* 整体降透明度 —— 一眼就是"还不能用" */
  opacity: 0.5;
}

.social-item {
  display: inline-flex;
  border-radius: 50%;
  cursor: not-allowed;
}

.social-btn {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 50%;
  background: var(--color-card);
  color: var(--color-text-muted);
  box-shadow:
    0 0 0 1px var(--color-border),
    0 3px 10px var(--color-shadow);
  cursor: not-allowed;
  /* 原生 disabled 按钮不派发 click,得把命中让给外层 .social-item,
     点击提示才弹得出来(否则"点了没反应") */
  pointer-events: none;
}

.social-btn svg {
  display: block;
  width: 19px;
  height: 19px;
  fill: currentColor;
}

/* =============================================================
 * 8. 底部插槽(注册页的「已有账号?去登录」)
 * ============================================================= */
.auth-foot {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-muted);
}

/* =============================================================
 * 9. PC 增强(≥901px)
 *
 * 分栏本身已经是默认样式(见第 2 节),这里只做两件 PC 专属的事:
 *   1. 页面四周留出更大的边距 —— 宽屏上用 20px 的页边距会显得"顶格"
 *   2. 卡片从 350px 放宽到 380px、内边距同步加一档:多出来的 30px 全给
 *      输入框,标签在上的排版会更松快(验证码那一行本来是最挤的)
 * ============================================================= */
@media (min-width: 901px) {
  .auth-page {
    padding: 48px 40px;
  }

  .auth-card {
    max-width: 380px;
    padding: 30px 38px;
  }
}

/* =============================================================
 * 10. ≤900px:回到单列(平板 / 手机)
 *
 * 左半屏整块撤掉 —— 900px 以下两列并排时,左半屏会被挤到只剩
 * 300px 出头,插画和文案都得重新排版,不如干脆不要。
 * 撤掉之后就是原来那张居中卡片,`.auth-page` 的 place-items:center
 * 已经负责居中,这里只需要把网格换成块级 + 卡片自己 margin auto。
 * ============================================================= */
@media (max-width: 900px) {
  .auth-split {
    display: block;
    max-width: 100%;
  }

  .auth-visual {
    display: none;
  }

  .auth-card {
    margin: 0 auto;
  }
}

/* =============================================================
 * 11. 手机 ≤600px:圆角收小、内边距收小、卡片撑满可用宽度
 *     (这一档的手感与本次改动前完全一致)
 * ============================================================= */
@media (max-width: 600px) {
  /* 返回首页:手机上把内边距和字号收一档,别占掉卡片上方的空间 */
  .auth-back {
    top: 14px;
    left: 14px;
    padding: 7px 12px;
    font-size: 12px;
  }

  .auth-page {
    padding: 20px 16px;
  }

  .auth-card {
    max-width: 100%;
    padding: 22px 20px;
    border-radius: 28px;
  }

  .auth-head {
    margin-bottom: 18px;
  }

  .auth-logo {
    width: 46px;
    height: 46px;
    font-size: 23px;
  }

  .social-btn {
    width: 42px;
    height: 42px;
  }
}
</style>
