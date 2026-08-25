/**
 * v-reveal  -  scroll-driven 入场动画指令
 *
 * 用法:
 *   <div v-reveal>...</div>
 *   <article v-reveal="{ delay: 100 }">...</article>   ← 100ms 延迟,用于错峰
 *   <section v-reveal="{ threshold: 0.3, rootMargin: '0px' }">...</section>
 *
 * 行为:
 *   1. 元素挂载时立即加 .reveal-up 类 → 默认 opacity:0 + translateY(40px)
 *   2. IntersectionObserver 监听元素进入视口
 *   3. 进入时加 .is-visible → 触发 CSS 过渡(opacity 1 + translateY(0))
 *   4. 触发一次后 unobserve(one-shot,不再重复)
 *
 * a11y:
 *   - prefers-reduced-motion 已被全局 CSS 处理(theme.css),把 transition 缩到 0.01ms,
 *     对偏好减弱动态效果的用户,reveal 几乎是瞬时的
 */

import type { Directive } from 'vue'

export interface RevealOptions {
  
  delay?: number
  
  threshold?: number
  
  rootMargin?: string
}

// WeakMap 持有 observer 引用,保证 unmounted 时能 disconnect
const observers = new WeakMap<Element, IntersectionObserver>()

function applyRevealClass(el: HTMLElement): void {
  el.classList.add('reveal-up')
}

function setupObserver(
  el: HTMLElement,
  options: Required<Pick<RevealOptions, 'delay' | 'threshold' | 'rootMargin'>>,
): void {
  const observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue

        // 错峰延迟  -  通过 inline transition-delay 控制
        if (options.delay > 0) {
          el.style.transitionDelay = `${options.delay}ms`
        }

        // 触发动画:加 .is-visible 类,触发 CSS transition
        el.classList.add('is-visible')

        // one-shot:触发后停止观察,避免反复触发
        observer.unobserve(el)
        observers.delete(el)
      }
    },
    {
      threshold: options.threshold,
      rootMargin: options.rootMargin,
    },
  )

  observer.observe(el)
  observers.set(el, observer)
}

export const vReveal: Directive<HTMLElement, RevealOptions | undefined> = {
  // 在绑定元素的父组件
  // 及他自己的所有子节点都挂载完成后调用
  mounted(el, binding) {
    const opts = binding.value ?? {}
    const normalized: Required<Pick<RevealOptions, 'delay' | 'threshold' | 'rootMargin'>> = {
      // ?? 空值合并运算符:如果左边是 null 或 undefined,就取右边的值
      delay: opts.delay ?? 0,
      threshold: opts.threshold ?? 0.15,
      rootMargin: opts.rootMargin ?? '0px 0px -10% 0px',
    }

    applyRevealClass(el)
    setupObserver(el, normalized)
  },

  // 绑定元素的父组件卸载后调用
  unmounted(el) {
    const observer = observers.get(el)
    if (observer) {
      observer.disconnect()
      observers.delete(el)
    }
  },
}