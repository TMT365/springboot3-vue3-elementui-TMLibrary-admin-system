<script setup lang="ts">
/**
 * PaymentDialog  -  选择支付方式
 *
 * 用在「我的订单」里点「去支付」时弹出。选中一种方式 → 确认 → 调
 * `purchaseApi.pay(orderNumber, method)`。
 *
 * <h2>为什么图标是内联 SVG 而不是图片文件</h2>
 * 三个支付标识都是几何图形,用 SVG 画:
 *   - **不会糊** —— 位图在 2x/3x 屏上要么发虚要么得多带一套 @2x 资源,
 *     SVG 是矢量,任何缩放都是清晰的(比"高清图"更彻底);
 *   - **没有外部请求** —— 图片要占一个 HTTP 请求,还可能被 CDN / 缓存策略坑;
 *   - **能跟着主题走** —— 内联 SVG 可以用 currentColor 和 CSS 变量,
 *     暗色下不用换一套图。
 *
 * ⚠️ 这些是**手绘的简化标识**,不是官方矢量素材。用于"选择支付方式"这种
 * 指示性场景没问题(商标的指示性合理使用),但如果要正式商用,
 * 应当替换为各支付平台官方提供的素材,并遵守其品牌规范。
 *
 * <h2>图标配色</h2>
 * 每个标识用**自己的品牌色做底 + 白色字形**,而不是直接画彩色 logo ——
 * 这样在亮色和暗色主题下对比度都稳定(白字压在饱和色块上),
 * 不需要为暗色单独调一遍颜色。
 */

import { computed, ref, watch } from 'vue'

const props = defineProps<{
  /** 是否显示(v-model) */
  modelValue: boolean
  /** 正在支付的订单号 —— 只用于展示 */
  orderNumber: string
  /** 订单金额(已格式化好的字符串) —— 只用于展示 */
  amount: string
  /** 请求中(父组件控制,禁用按钮防重复提交) */
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /** 用户确认支付,带上选中的支付方式 code */
  confirm: [method: string]
}>()

interface Method {
  /** 传给后端的 code —— 必须和 PaymentMethod 枚举对得上 */
  code: string
  name: string
  /** 品牌色:图标底色 + 选中态的描边/发光 */
  color: string
  /** 24x24 视野下的图形(SVG 片段,白色填充) */
  svg: string
}

/**
 * 三种支付方式。
 *
 * svg 里存的是**路径片段**而不是整个 <svg> —— 外层统一控制 viewBox / 尺寸 / 填充色,
 * 三种图标才能保证视觉重量一致(手写三个完整 svg 很容易一大一小)。
 */
const METHODS: Method[] = [
  {
    code: 'WECHAT',
    name: '微信支付',
    color: '#07C160',
    // 微信:两个叠放的对话气泡 + 四点眼
    svg:
      '<ellipse cx="9" cy="9.2" rx="7.2" ry="6.1"/>' +
      '<path d="M4.6 13.6 3 17.4l3.9-1.6z"/>' +
      '<ellipse cx="15.4" cy="14.6" rx="7.4" ry="6.3"/>' +
      '<path d="M19.8 19.3 21.4 23l-4-1.7z"/>' +
      '<circle cx="6.6" cy="8.4" r="1.15" fill="#07C160"/>' +
      '<circle cx="11.4" cy="8.4" r="1.15" fill="#07C160"/>' +
      '<circle cx="13" cy="13.8" r="1.15" fill="#07C160"/>' +
      '<circle cx="17.8" cy="13.8" r="1.15" fill="#07C160"/>',
  },
  {
    code: 'ALIPAY',
    name: '支付宝',
    color: '#1677FF',
    // 支付宝:圆角方底 + 「支」字(官方标识本身就是这个字)
    svg:
      '<rect x="2" y="2" width="20" height="20" rx="5.5"/>' +
      '<text x="12" y="17.2" text-anchor="middle" font-size="14.5" font-weight="700" ' +
      'font-family="PingFang SC, Microsoft YaHei, sans-serif" fill="#1677FF">支</text>',
  },
  {
    code: 'QQ',
    name: 'QQ 钱包',
    color: '#12B7F5',
    // QQ:企鹅剪影(头身一体 + 两鳍 + 喙 + 脚)
    svg:
      '<path d="M12 1.9c-3.5 0-5.9 2.5-5.9 5.9 0 1 .2 1.9.5 2.7' +
      '-1.5 1.6-2.4 4-2.4 6.5 0 2 1.5 3.6 3.4 3.6h8.8c1.9 0 3.4-1.6 3.4-3.6 0-2.5-.9-4.9-2.4-6.5' +
      '.3-.8.5-1.7.5-2.7 0-3.4-2.4-5.9-5.9-5.9z"/>' +
      '<ellipse cx="9.5" cy="7.4" rx="1.35" ry="1.75" fill="#12B7F5"/>' +
      '<ellipse cx="14.5" cy="7.4" rx="1.35" ry="1.75" fill="#12B7F5"/>' +
      '<path d="M12 10.3 10.6 12h2.8z" fill="#12B7F5"/>' +
      '<ellipse cx="7.2" cy="20.4" rx="2.3" ry="1.2"/>' +
      '<ellipse cx="16.8" cy="20.4" rx="2.3" ry="1.2"/>',
  },
]

/** 当前选中的方式,默认第一种 —— 进弹窗就有选中态,不用先点一下才能确认 */
const selected = ref<string>(METHODS[0].code)

/* 每次打开都重置成默认选中,避免上次的选择留在下一次支付上
   (用户很容易没注意上次选的是哪个,直接点确认就付错方式) */
watch(
  () => props.modelValue,
  (open) => {
    if (open) selected.value = METHODS[0].code
  },
)

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

function onConfirm(): void {
  emit('confirm', selected.value)
}
</script>

<template>
  <el-dialog
    v-model="visible"
    width="min(460px, 92vw)"
    align-center
    :close-on-click-modal="false"
    class="pay-dialog"
    aria-label="选择支付方式"
  >
    <div class="pay-body">
      <h2 class="pay-title">选择支付方式</h2>
      <p class="pay-amount">
        <span class="pay-amount-label">应付金额</span>
        <span class="pay-amount-value">{{ amount }}</span>
      </p>

      <!-- role=radiogroup:这是一组单选,键盘用户能用方向键切换 -->
      <div class="pay-methods" role="radiogroup" aria-label="支付方式">
        <button
          v-for="m in METHODS"
          :key="m.code"
          type="button"
          class="pay-method"
          :class="{ 'is-active': selected === m.code }"
          :style="{ '--brand': m.color }"
          role="radio"
          :aria-checked="selected === m.code"
          @click="selected = m.code"
        >
          <span class="pay-icon" aria-hidden="true">
            <!-- viewBox 统一 24x24;fill=currentColor 让整块图形跟随 .pay-icon 的颜色 -->
            <svg viewBox="0 0 24 24" fill="currentColor" v-html="m.svg" />
          </span>
          <span class="pay-name">{{ m.name }}</span>
          <span class="pay-check" aria-hidden="true">
            <el-icon><Select /></el-icon>
          </span>
        </button>
      </div>

      <p class="pay-tip">
        <el-icon><InfoFilled /></el-icon>
        <span>演示环境,不会产生真实扣款</span>
      </p>
    </div>

    <template #footer>
      <el-button size="large" @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        size="large"
        class="pay-confirm"
        :loading="loading"
        @click="onConfirm"
      >
        确认支付
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.pay-body {
  display: flex;
  flex-direction: column;
  padding: 4px 2px 0;
}

.pay-title {
  margin: 0 0 6px;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text);
  text-align: center;
}

.pay-amount {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8px;
  margin: 0 0 20px;
}

.pay-amount-label {
  font-size: 12.5px;
  color: var(--color-text-soft);
}

.pay-amount-value {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 24px;
  font-weight: 700;
  color: var(--color-accent);
  font-variant-numeric: tabular-nums;
}

.pay-methods {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 一行一个:每个选项要放图形 + 名称 + 选中标记,横排三个在手机上会挤成一条 */
.pay-method {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px;
  border: 1px solid var(--color-border);
  border-radius: 14px;
  background: var(--color-bg-alt);
  cursor: pointer;
  text-align: left;
  font-size: 14px;
  color: var(--color-text);
  transition:
    border-color 180ms ease,
    background 180ms ease,
    box-shadow 180ms ease;
}

.pay-method:hover {
  border-color: var(--brand);
}

/* 选中态:品牌色描边 + 一圈同色淡光,不整块染色(整块染色会盖掉品牌色本身) */
.pay-method.is-active {
  border-color: var(--brand);
  background: var(--color-card);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--brand) 18%, transparent);
}

.pay-method:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.pay-icon {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  /* 品牌色底 + 白色图形:亮暗两套主题下对比度都稳定 */
  background: var(--brand);
  color: #fff;
}

.pay-icon svg {
  width: 24px;
  height: 24px;
  display: block;
}

.pay-name {
  flex: 1;
  font-weight: 500;
}

/* 选中标记:默认透明,选中才出现 —— 用 visibility 而不是 v-if,
   避免出现/消失时整行高度跳动 */
.pay-check {
  flex-shrink: 0;
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  font-size: 13px;
  opacity: 0;
  transform: scale(0.7);
  transition:
    opacity 160ms ease,
    transform 160ms cubic-bezier(0.22, 1, 0.36, 1);
}

.pay-method.is-active .pay-check {
  opacity: 1;
  transform: scale(1);
}

.pay-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 16px 0 0;
  font-size: 12px;
  color: var(--color-text-soft);
}

.pay-confirm {
  min-width: 120px;
  border: none;
  border-radius: 18px;
  font-weight: 600;
  background: linear-gradient(
    135deg,
    var(--color-accent) 0%,
    var(--color-accent-hover) 100%
  );
}

/* 暗色下 --color-bg-alt 和弹窗底色太接近,选项会"陷"进去 —— 提亮一档 */
:root[data-theme='dark'] .pay-method {
  background: #262626;
  border-color: #343434;
}

:root[data-theme='dark'] .pay-method.is-active {
  background: #2e2e2e;
}

:root[data-theme='dark'] .pay-tip {
  color: var(--color-text-muted);
}
</style>
