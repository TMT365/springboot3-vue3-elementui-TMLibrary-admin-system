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