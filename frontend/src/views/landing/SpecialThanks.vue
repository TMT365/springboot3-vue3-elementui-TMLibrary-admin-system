<script setup lang="ts">
/**
 * SpecialThanks  -  特别鸣谢横向滚动条(v4)
 *
 * 放置位置:Contact 之后、Footer 之前
 *
 * 视觉差异(v4 相对于 v3):
 *   占位字母头像 → 官网官方 SVG 标志(通过 Simple Icons CDN)
 *   12 项内容:本项目所用技术栈 + AI
 *
 * 内容分类(以出现顺序滚动):
 *   - 前端栈:Vue.js、TypeScript、Element Plus、Vite
 *   - 后端栈:Spring Boot、Java、MySQL、Maven
 *   - 工具栈:IntelliJ IDEA、Git、GitHub
 *   - AI 协作:Claude(Anthropic)
 *
 * 主题适配:
 *   - 边框 / 斜线 → --stripe-color(亮色 = 黑,暗色 = 白)
 *   - Logo → 默认品牌色,暗色下用 filter: invert(1) 反色
 *     (Simple Icons 的 GitHub/JetBrains/Anthropic 等深色 logo
 *      在暗色背景下不反色会看不见)
 */

interface Credit {
  id: string
  name: string
  slug: string /* Simple Icons CDN 的图标 ID */
  shape: 'circle' | 'square'
  showText?: boolean
}

const credits: Credit[] = [
  // 前端栈
  { id: 'vue',     name: 'Vue.js',        slug: 'vuedotjs',    shape: 'circle', showText: true  },
  { id: 'ts',      name: 'TypeScript',    slug: 'typescript',  shape: 'circle', showText: true  },
  { id: 'element', name: 'Element Plus',  slug: 'element',     shape: 'square', showText: true  },
  { id: 'vite',    name: 'Vite',          slug: 'vite',        shape: 'circle', showText: false },

  // 后端栈
  { id: 'spring',  name: 'Spring Boot',   slug: 'spring',      shape: 'circle', showText: true  },
  { id: 'java',    name: 'Java',          slug: 'openjdk',     shape: 'square', showText: true  },
  { id: 'mysql',   name: 'MySQL',         slug: 'mysql',       shape: 'square', showText: true  },
  { id: 'maven',   name: 'Maven',         slug: 'apachemaven', shape: 'square', showText: false },

  // 工具栈
  { id: 'idea',    name: 'IntelliJ IDEA', slug: 'jetbrains',   shape: 'square', showText: true  },
  { id: 'git',     name: 'Git',           slug: 'git',         shape: 'circle', showText: false },
  { id: 'github',  name: 'GitHub',        slug: 'github',      shape: 'square', showText: false },

  // AI 协作
  { id: 'claude',  name: 'Claude',        slug: 'anthropic',   shape: 'circle', showText: true  },
]

/* 复制一份 - 配合 translateX(-50%) 实现无缝循环 */
const doubledCredits: Credit[] = [...credits, ...credits]

/* Simple Icons CDN:https://cdn.simpleicons.org/[slug] → 官方 SVG 标志 */
function makeAvatar(c: Credit): string {
  return `https://cdn.simpleicons.org/${c.slug}`
}
</script>

<template>
  <section id="thanks" class="thanks" v-reveal>
    <header class="thanks-header">
      <span class="thanks-eyebrow">Acknowledgments · 致谢</span>
      <h2 class="thanks-title">特别鸣谢</h2>
      <p class="thanks-sub">每一份支持都值得被记住</p>
    </header>

    <div class="thanks-marquee" aria-label="特别鸣谢名单">
      <!-- 顶部:外层 div(顶 2px + 底 2px 纯黑/白)包着 12px 高的斜纹 -->
      <div class="stripe-outer stripe-outer--top" aria-hidden="true">
        <div class="stripe-fill" />
      </div>

      <div class="thanks-track" role="list">
        <div
          v-for="(credit, i) in doubledCredits"
          :key="`${credit.id}-${i}`"
          class="thanks-item"
          role="listitem"
          :title="credit.name"
        >
          <img
            :src="makeAvatar(credit)"
            :alt="credit.name"
            class="thanks-avatar"
            :class="`thanks-avatar--${credit.shape}`"
            loading="lazy"
            decoding="async"
          />
          <span v-if="credit.showText" class="thanks-name">{{ credit.name }}</span>
        </div>
      </div>

      <!-- 底部:同上,动画方向反向 -->
      <div class="stripe-outer stripe-outer--bottom" aria-hidden="true">
        <div class="stripe-fill" />
      </div>
    </div>
  </section>
</template>

<style scoped>
/* =============================================================
 * 整体布局  -  脱离 1200px 居中容器
 * - 标题居左、字号 clamp(56-112px)
 * - Marquee 100% 宽(edge-to-edge)
 * ============================================================= */
.thanks {
  width: 100%;
  padding: clamp(80px, 12vw, 140px) 0 0;
}

.thanks-header {
  max-width: 1200px;
  margin: 0 0 56px;
  padding: 0 24px;
  text-align: left;
}

.thanks-eyebrow {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-text-soft);
  margin-bottom: 20px;
}

.thanks-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: clamp(56px, 8vw, 112px);
  font-weight: 400;
  line-height: 0.95;
  letter-spacing: -0.03em;
  margin: 0 0 20px;
  color: var(--color-text);
}

.thanks-sub {
  font-size: 17px;
  line-height: 1.6;
  color: var(--color-text-muted);
  margin: 0;
  max-width: 560px;
}

/* =============================================================
 * Marquee  -  100% 宽
 *
 * 高度构成(桌面):
 *   .stripe-outer--top   16px  ( 2px 边 + 12px 斜线 + 2px 边 )
 *   .thanks-item         48px
 *   .stripe-outer--bottom 16px
 *   =                    80px
 * ============================================================= */
.thanks-marquee {
  position: relative;
  width: 100%;
  height: clamp(80px, 6vw, 96px);
  overflow: hidden;
  --stripe-color: #000;
}

:root[data-theme='dark'] .thanks-marquee {
  --stripe-color: #fff;
}

/* =============================================================
 * Stripe Outer  -  真实 div 套在条纹外面
 *
 *   ┌───────────────────────┐  ←  2px var(--stripe-color)(顶)
 *   │░░░░░░░░░░░░░░░░░░░░░░░│  ←  12px 斜线(.stripe-fill)
 *   └───────────────────────┘  ←  2px var(--stripe-color)(底)
 * ============================================================= */
.stripe-outer {
  position: absolute;
  left: 0;
  right: 0;
  height: 16px;
  display: flex;
  align-items: stretch;
  pointer-events: none;
  z-index: 2;
  background: linear-gradient(
    to bottom,
    var(--stripe-color) 0,
    var(--stripe-color) 2px,
    transparent 2px,
    transparent 14px,
    var(--stripe-color) 14px,
    var(--stripe-color) 16px
  );
}

.stripe-outer--top {
  top: 0;
}

.stripe-outer--bottom {
  bottom: 0;
}

.stripe-fill {
  flex: 1;
  height: 12px;
  align-self: center;
  background-image: repeating-linear-gradient(
    135deg,
    var(--stripe-color) 0,
    var(--stripe-color) 18px,
    transparent 18px,
    transparent 32px
  );
  background-size: 200% 100%;
  background-repeat: repeat-x;
}

.stripe-outer--top .stripe-fill {
  animation: stripe-march 24s linear infinite;
}

.stripe-outer--bottom .stripe-fill {
  animation: stripe-march 24s linear infinite reverse;
}

@keyframes stripe-march {
  from { background-position: 0 0; }
  to   { background-position: 200% 0; }
}

/* =============================================================
 * Track  -  头像 + 可选文字,从右向左平动
 * ============================================================= */
.thanks-track {
  display: flex;
  align-items: center;
  height: 100%;
  gap: 48px;
  width: max-content;
  padding: 0 24px;
  animation: thanks-scroll 60s linear infinite;
  will-change: transform;
}

.thanks-item {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
  height: 48px;
}

/* =============================================================
 * Avatar  -  官方 SVG 标志(替换占位字母头像)
 *
 *   48×48 容器(theme-aware 背景)
 *     ├── 1px 主题色边框
 *     └── 10px / 8px 内边距 → SVG 居中填充剩余空间
 *
 *   暗色主题下用 filter: invert(1) 把深色品牌色(黑/近黑)反成白
 *   中等亮度品牌色(Vue 绿、TS 蓝等)反色后仍是中色,清晰可见
 * ============================================================= */
.thanks-avatar {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  object-fit: contain;
  background: var(--color-card);
  display: block;
  border: 1px solid var(--color-border);
  box-sizing: border-box;
  padding: 10px;
  transition: transform 200ms ease;
}

.thanks-avatar--circle {
  border-radius: 50%;
  padding: 8px; /* 圆形留白多一点,避免 logo 角被切得太狠 */
}

.thanks-avatar--square {
  border-radius: 6px;
}

/* 暗色:把 SVG 本身的 fill 反色,确保深色 logo(GitHub/JetBrains/Anthropic)可见 */
:root[data-theme='dark'] .thanks-avatar {
  filter: invert(1);
}

.thanks-name {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-text);
  white-space: nowrap;
  user-select: none;
}

@keyframes thanks-scroll {
  from { transform: translateX(0); }
  to   { transform: translateX(-50%); }
}

.thanks-marquee:hover .thanks-track {
  animation-play-state: paused;
}

/* =============================================================
 * 响应式 + 减弱动效
 * ============================================================= */
@media (max-width: 768px) {
  .thanks-title {
    font-size: clamp(48px, 12vw, 72px);
  }
  .thanks-marquee {
    height: 64px; /* 12 + 40 + 12 */
  }
  .stripe-outer {
    height: 12px;
    background: linear-gradient(
      to bottom,
      var(--stripe-color) 0,
      var(--stripe-color) 1px,
      transparent 1px,
      transparent 11px,
      var(--stripe-color) 11px,
      var(--stripe-color) 12px
    );
  }
  .stripe-fill {
    height: 10px;
    background-image: repeating-linear-gradient(
      135deg,
      var(--stripe-color) 0,
      var(--stripe-color) 14px,
      transparent 14px,
      transparent 24px
    );
  }
  .stripe-outer--top .stripe-fill,
  .stripe-outer--bottom .stripe-fill {
    animation-duration: 18s;
  }
  .thanks-track {
    gap: 28px;
    animation-duration: 42s;
  }
  .thanks-item {
    height: 40px;
  }
  .thanks-avatar {
    width: 40px;
    height: 40px;
    padding: 7px;
  }
  .thanks-avatar--circle {
    padding: 6px;
  }
  .thanks-name {
    font-size: 14px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .stripe-outer--top .stripe-fill,
  .stripe-outer--bottom .stripe-fill,
  .thanks-track {
    animation: none;
  }
}
</style>
