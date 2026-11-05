/**
 * 格式化工具集  -  价格/日期统一出口,避免散落各处
 *
 * 设计要点:
 * - price / totalAmount 后端是 BigDecimal 序列化为字符串,前端不要直接当 number 算
 * - formatPrice 容忍 null/undefined,UI 列表渲染时不用每处加判空
 * - 日期格式固定 "yyyy-MM-dd" / "yyyy-MM-ddTHH:mm:ss",后端统一 ISO-8601
 */


export function formatPrice(value: string | number | null | undefined): string {
  if (value == null) return '¥0.00'
  const n = typeof value === 'string' ? Number(value) : value
  if (!Number.isFinite(n)) return '¥0.00'
  return `¥${n.toFixed(2)}`
}


export function parsePrice(value: string): number {
  return Number(value)
}


export function formatDate(iso: string | null | undefined): string {
  if (!iso) return ''
  return iso.slice(0, 10)
}


export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return ''
  return iso.replace('T', ' ')
}

/**
 * 时间戳拆成「日期 / 时刻」两段,给表格里的两行时间单元用。
 *
 * <h2>为什么拆</h2>
 * 后端给的是 "2026-09-18T23:53:06" 这种 ISO 串,直接塞进一格要么撑爆列宽、
 * 要么被省略号切掉后半截(2026-09-18 23:53:… 看不出是几点)。
 * 拆成两行后:日期是给人扫的,时刻是给人对齐的,一眼能比"谁更晚"。
 *
 * @param iso ISO 时间串,允许 null/undefined
 * @returns 日期段 "YYYY-MM-DD";时刻段 "HH:mm:ss";空值返回 ''
 */
export function formatDatePart(iso: string | null | undefined): string {
  if (!iso) return ''
  return iso.slice(0, 10)
}

export function formatTimePart(iso: string | null | undefined): string {
  if (!iso) return ''
  // ISO 串第 11 位起是 "HH:mm:ss"("2026-09-18T23:53:06" → 索引 11 = '2')
  return iso.slice(11, 19)
}

/**
 * 取用户名的首字符,给头像占位用(MallLayout / AdminLayout / UserProfile 共用)。
 *
 * <h2>为什么不用 str.slice(0, 1)</h2>
 * JS 的字符串下标是按 <b>UTF-16 code unit</b> 切的,不是按字符。BMP 之外的字符
 * (emoji / 生僻字)占两个 code unit,slice(0,1) 会切出半个代理对,
 * 渲染成一个「�」。用展开运算符按 <b>code point</b> 迭代就没这个问题。
 *
 * 中文用户名本来 slice(0,1) 是对的(一个汉字一个 code unit),所以这不是
 * 「中文乱码」的根因 —— 那个在 stores/user.ts 的 JWT 解码里。
 * 这里只是顺手把边界补上,顺带把三处重复的写法收敛成一处。
 *
 * @param name 用户名,允许 null/undefined
 * @returns 首字符(大写化只对拉丁字母生效,中文原样返回);空值返回 '?'
 */
export function initialOf(name: string | null | undefined): string {
  if (!name) return '?'
  const first = [...name][0] ?? ''
  return first.toUpperCase()
}
