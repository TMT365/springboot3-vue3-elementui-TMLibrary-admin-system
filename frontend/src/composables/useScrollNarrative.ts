/**
 * useScrollNarrative —— 落地页滚动叙事
 *
 * 职责(两件事,都只服务 Landing 长页面):
 *   1. 阅读进度(progress):整页滚了多少,0..1。喂给顶部细进度条。
 *   2. 当前章节(activeSection):正在视口里的叙事区块 id,喂给右侧章节导航轨
 *      的高亮;点轨上某一节,平滑滚到该节开头。
 *
 * 实现原则(与 BackToTop 同源,见其注释):
 *   - 尺寸相关(scrollHeight)只在变化时算、缓存,不在 scroll 回调里读 ——
 *     避免布局抖动。
 *   - scroll 只读 window.scrollY(不触发布局),再经 rAF 节流到每帧一次。
 *   - 当前章节用 IntersectionObserver 判断"哪一节占据视口中线",而不是手算
 *     偏移量 —— 区块高度不一(hero 满屏、project-structure 很长),手算易错,
 *     observer 对布局变化天然鲁棒。
 *
 * a11y:
 *   - prefers-reduced-motion 下:进度与高亮照常更新(信息不丢),只是点击跳转
 *     用 instant 而不是 smooth。动画减弱由全局 theme.css §5 处理进度条的过渡。
 */

import { ref, onMounted, onBeforeUnmount, type Ref } from 'vue'

export interface NarrativeSection {
  /** 区块锚点,同时是 DOM 元素 id(供 querySelector 定位) */
  id: string
  /** 章节导航轨上显示的短标题 */
  label: string
}

/** 落地页叙事区块,按页面顺序 —— 与模板里 section 顺序一致 */
export const NARRATIVE_SECTIONS: NarrativeSection[] = [
  { id: 'home', label: '首页' },
  { id: 'showcase', label: '展示' },
  { id: 'features', label: '功能' },
  { id: 'get-started', label: '开始' },
  { id: 'learning-log', label: '学习' },
  { id: 'project-structure', label: '结构' },
  { id: 'join-us', label: '加入' },
  { id: 'contact', label: '联系' },
]

/** 顶部 sticky 导航高度 —— 章节跳转要让它开,否则锚点被导航盖住 */
const NAV_H = 72

/** prefers-reduced-motion:减弱模式下平滑滚动对部分用户会晕车 */
const prefersReducedMotion = (): boolean =>
  typeof window !== 'undefined' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

export function useScrollNarrative(): {
  progress: Ref<number>
  activeSection: Ref<string>
  scrollToSection: (id: string) => void
  sections: NarrativeSection[]
} {
  const progress = ref(0)
  const activeSection = ref('home')

  /** 可滚动总高度缓存 —— 只在尺寸变化时重算 */
  let maxScroll = 0
  /** rAF 句柄,兼作"本帧已排队"的标记 */
  let frame = 0
  let observer: IntersectionObserver | null = null
  let resizeObserver: ResizeObserver | null = null

  function measure(): void {
    maxScroll = Math.max(
      document.documentElement.scrollHeight - window.innerHeight,
      0,
    )
  }

  function updateProgress(): void {
    frame = 0
    const y = window.scrollY
    progress.value = maxScroll > 0 ? Math.min(y / maxScroll, 1) : 0
  }

  function onScroll(): void {
    if (frame) return
    frame = requestAnimationFrame(updateProgress)
  }

  function onResize(): void {
    measure()
    onScroll()
  }

  onMounted(() => {
    resizeObserver = new ResizeObserver(onResize)
    resizeObserver.observe(document.body)

    window.addEventListener('scroll', onScroll, { passive: true })
    window.addEventListener('resize', onResize)

    // 观察各叙事区块:谁的中线越过了视口中线,谁就是"当前章节"。
    // 用 -40% 的负 rootMargin 让判断点略微上移 —— 章节进入视口一段后
    // 才高亮,避免区块刚露出 1px 就切过去。
    observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue
          activeSection.value = entry.target.id
        }
      },
      {
        rootMargin: '-40% 0px -55% 0px',
        threshold: 0,
      },
    )
    for (const sec of NARRATIVE_SECTIONS) {
      const el = document.getElementById(sec.id)
      if (el) observer.observe(el)
    }

    measure()
    updateProgress()
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    resizeObserver?.disconnect()
    window.removeEventListener('scroll', onScroll)
    window.removeEventListener('resize', onResize)
    if (frame) cancelAnimationFrame(frame)
  })

  /** 跳到某章节;减弱模式下用 instant,否则 smooth,并让开导航高度 */
  function scrollToSection(id: string): void {
    const el = document.getElementById(id)
    if (!el) return
    const top = el.getBoundingClientRect().top + window.scrollY - NAV_H
    window.scrollTo({
      top,
      behavior: prefersReducedMotion() ? 'instant' : 'smooth',
    })
  }

  return {
    progress,
    activeSection,
    scrollToSection,
    sections: NARRATIVE_SECTIONS,
  }
}