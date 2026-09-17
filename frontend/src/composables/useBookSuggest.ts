import { bookApi } from '@/api/book'
import type { BookSuggestion } from '@/types/api'

/**
 * 搜索候选词(下拉建议)—— 顶栏搜索框和手机端搜索框共用。
 *
 * <h2>防抖在哪</h2>
 * <p>用 Element Plus `el-autocomplete` 自带的 {@code :debounce}(默认 300ms):
 * 它内部对 `fetch-suggestions` 做了节流,不用自己写 setTimeout。本 composable 只负责
 * <b>取数 + 竞态防护</b>。</p>
 *
 * <h2>为什么要防竞态</h2>
 * <p>防抖只保证"不会每敲一个字都发请求",不保证响应按顺序回来:
 * 输入「计算」时发了请求 A,紧接着改成「计算机」发了请求 B,
 * 如果 A 比 B 晚回来,下拉里显示的就是「计算」的结果 —— 用户看着自己打的字,
 * 却是别人的候选。所以每次请求带一个自增序号,回来时序号对不上就丢弃。</p>
 */

/** 高亮切分后的一段文本 */
export interface HighlightPart {
  text: string
  /** true = 命中搜索词的那一段,前端加粗/变色 */
  hit: boolean
}

/**
 * 把候选词按搜索词切成"命中 / 未命中"几段 —— 下拉里高亮匹配部分。
 * 只切第一处匹配(候选词都很短,多处匹配的收益不值当)。
 */
export function highlightParts(text: string, query: string): HighlightPart[] {
  const q = query.trim()
  if (!q) return [{ text, hit: false }]

  const idx = text.toLowerCase().indexOf(q.toLowerCase())
  if (idx < 0) return [{ text, hit: false }]

  const parts: HighlightPart[] = []
  if (idx > 0) parts.push({ text: text.slice(0, idx), hit: false })
  parts.push({ text: text.slice(idx, idx + q.length), hit: true })
  const rest = text.slice(idx + q.length)
  if (rest) parts.push({ text: rest, hit: false })
  return parts
}

export function useBookSuggest() {
  /** 请求序号 —— 只认最后一次请求的结果 */
  let seq = 0

  /**
   * 喂给 el-autocomplete 的 fetch-suggestions。
   * 签名由 EP 规定:(输入内容, 回调) => void,结果通过回调交回去。
   */
  async function fetchSuggestions(
    query: string,
    cb: (items: BookSuggestion[]) => void,
  ): Promise<void> {
    const keyword = query.trim()
    const mine = ++seq

    if (!keyword) {
      cb([])
      return
    }

    try {
      const items = await bookApi.suggest(keyword)
      if (mine !== seq) return // 期间又敲了新的,这份结果已经过期
      cb(items)
    } catch {
      // 候选词失败不该打断输入 —— 安静地给个空下拉,用户回车还能走完整搜索
      if (mine === seq) cb([])
    }
  }

  return { fetchSuggestions }
}
