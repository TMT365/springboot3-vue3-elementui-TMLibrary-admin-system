/**
 * 购物车  -  前端 mock 持久化(Pinia + localStorage)
 *
 * 设计:
 * - 模块级单例:多个组件 useCartStore() 拿到同一个 state
 * - localStorage key: 'tm_user_cart' 跨会话保留(关浏览器再开还在)
 * - 持久化时机:任何 addItem / removeItem / updateQuantity / clear 都立即写回
 * - 启动时不主动 init,Pinia 自动反序列化(ref 在 store init 时从 localStorage 读)
 *
 * 数据形状  -  CartItem 是 BookDto 的快照 + quantity + addedAt:
 *   - 下单时只把 { bookId, quantity } 传给 purchaseApi.create()(后端不需要其他字段)
 *   - 价格快照:加入购物车那一刻的 price,结算时仍是当时的价格(后端会再次校验)
 *
 * 跟后端无关 / 不调 API  -  这是纯前端 mock,后端 Cart 表不存在
 *
 * ⚠️ 安全提示:切到真实后端时,price / title 这些从后端拿,前端不再存(防篡改)
 */

import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import type { BookDto } from '@/types/api'

const STORAGE_KEY = 'tm_user_cart'

/**
 * 购物车单项  -  BookDto 的精简快照 + 数量 + 加入时间
 * 不存 createdTime / updatedTime(购物车用不到)
 */
export interface CartItem {
  bookId: number
  title: string
  author: string
  isbn: string
  /** 加入时的价格快照(BigDecimal 字符串,跟 BookDto.price 一致) */
  price: string
  /** 封面首字母 mark(从 title 派生,跟 UserBooks 的 book-cover-mark 保持一致) */
  coverMark: string
  quantity: number
  /** ISO 字符串  -  用于后续可能的「最近加入」排序,目前 UI 没用上但保留 */
  addedAt: string
}

/**
 * 从 localStorage 读  -  容错:JSON 损坏 / 版本不兼容时返回空数组
 */
function loadCart(): CartItem[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? (parsed as CartItem[]) : []
  } catch {
    return []
  }
}

/**
 * 从 BookDto 派生 CartItem  -  抽出来让 addItem 复用
 */
function bookToCartItem(book: BookDto): CartItem {
  return {
    bookId: book.id,
    title: book.title,
    author: book.author,
    isbn: book.isbn,
    price: book.price,
    coverMark: book.title.slice(0, 1).toUpperCase(),
    quantity: 1,
    addedAt: new Date().toISOString(),
  }
}

export const useCartStore = defineStore('cart', () => {
  /**
   * 启动时从 localStorage 反序列化
   * 注意:这里直接传 loadCart() 给 ref,Pinia 会接管后续响应式
   */
  const items = ref<CartItem[]>(loadCart())

  /** 衍生量  -  商品总件数(不是 SKU 数) */
  const totalItems = computed<number>(() =>
    items.value.reduce((sum, item) => sum + item.quantity, 0),
  )

  /** 衍生量  -  总价(¥xxx.xx 字符串) */
  const subtotal = computed<string>(() => {
    const total = items.value.reduce((sum, item) => {
      return sum + Number(item.price) * item.quantity
    }, 0)
    return total.toFixed(2)
  })

  /** 衍生量  -  是否为空 */
  const isEmpty = computed<boolean>(() => items.value.length === 0)

  /**
   * 持久化副作用  -  任何响应式变更都立即写回 localStorage
   * watch 的 flush: 'sync' 保证顺序(同步写回,跟 mutation 同时发生)
   * deep: true 因为数组里的对象会被修改
   */
  watch(
    items,
    (newItems) => {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(newItems))
      } catch {
        // 配额超限 / 隐私模式  -  静默失败,不抛错阻塞 UI
      }
    },
    { deep: true, flush: 'sync' },
  )

  /* ------------------- Actions ------------------- */

  /**
   * 加入购物车  -  如果该 bookId 已存在,数量 +1;否则新增
   * @returns 'added' | 'incremented'  -  给调用方决定弹什么文案
   */
  function addItem(book: BookDto): 'added' | 'incremented' {
    const existing = items.value.find((i) => i.bookId === book.id)
    if (existing) {
      existing.quantity += 1
      return 'incremented'
    }
    items.value.push(bookToCartItem(book))
    return 'added'
  }

  /** 减少一件(bookId 不存在时静默无操作) */
  function decrementItem(bookId: number): void {
    const existing = items.value.find((i) => i.bookId === bookId)
    if (!existing) return
    if (existing.quantity <= 1) {
      removeItem(bookId)
    } else {
      existing.quantity -= 1
    }
  }

  /** 直接设置数量  -  用于 el-input-number 双向绑定;<=0 等同删除 */
  function updateQuantity(bookId: number, quantity: number): void {
    const existing = items.value.find((i) => i.bookId === bookId)
    if (!existing) return
    if (quantity <= 0) {
      removeItem(bookId)
    } else {
      existing.quantity = quantity
    }
  }

  /** 删除单项 */
  function removeItem(bookId: number): void {
    items.value = items.value.filter((i) => i.bookId !== bookId)
  }

  /** 清空购物车  -  结算成功后调用 */
  function clear(): void {
    items.value = []
  }

  return {
    items,
    totalItems,
    subtotal,
    isEmpty,
    addItem,
    decrementItem,
    updateQuantity,
    removeItem,
    clear,
  }
})