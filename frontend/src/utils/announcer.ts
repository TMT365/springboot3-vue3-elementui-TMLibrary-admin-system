/**
 * 页面变更播报  -  单页应用(SPA)的无障碍基本功
 *
 * 问题:SPA 换页时地址栏变了、标题变了,但屏幕阅读器不知道发生了什么。
 * 读屏用户按完一个链接后,焦点还留在刚点过的那个链接上,
 * 他只能自己往回翻找新内容 —— 换页成了"没有提示"的黑箱。
 *
 * 标准做法(和 gov.uk / GOV.UK Design System 的路由播报一致):
 *   1. 换页后把焦点移到主内容区(main landmark)—— 读屏会念出它的内容
 *   2. 同时用一个 aria-live="polite" 的隐藏区域播报"已进入 XX 页"
 *
 * 这里只负责第 2 步(第 1 步在 router/index.ts 里做,需要等 DOM 更新)。
 *
 * 为什么不是每次都新建节点:
 * aria-live 区域必须在内容变化**之前**就已经存在于无障碍树里,
 * 临时插入的节点在部分读屏软件上不会被播报。所以这里复用一个常驻节点。
 */

let region: HTMLElement | null = null

function ensureRegion(): HTMLElement | null {
  if (typeof document === 'undefined') return null

  if (!region || !region.isConnected) {
    region = document.getElementById('app-announcer')
    if (!region) {
      region = document.createElement('div')
      region.id = 'app-announcer'
      region.className = 'sr-only'
      region.setAttribute('role', 'status')
      region.setAttribute('aria-live', 'polite')
      region.setAttribute('aria-atomic', 'true')
      document.body.appendChild(region)
    }
  }
  return region
}

/**
 * 播报一条短消息(会被读屏软件读出来)
 * @param message 例如「已进入图书管理」
 */
export function announce(message: string): void {
  const el = ensureRegion()
  if (!el) return

  // 先清空再写回:连续两次播报同一句话时,读屏软件只在文本"发生变化"时才念,
  // 直接赋值同样的字符串会被当成没变而跳过。
  el.textContent = ''
  window.setTimeout(() => {
    el.textContent = message
  }, 60)
}
