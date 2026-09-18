<script setup lang="ts">
/**
 * AuthCard  -  登录 / 注册共用的「圆角卡片」外壳
 *
 * 为什么单独抽一层:
 *   Login.vue / Register.vue 的卡片外观(40px 大圆角、多层柔和阴影、渐变底、
 *   内边距)完全一致,输入框 / 主按钮的样式也共用一套设计语言。写两份必然漂移,
 *   所以统一收在这里:
 *     - 页面底(居中 + 两团绿色光晕)
 *     - 卡片本体(head + 默认插槽 + 社交按钮 + footer 插槽)
 *     - 表单控件样式(输入框、主按钮)—— 用 :deep() 作用到插槽内容,
 *       页面里不用再抄一遍
 *
 * 插槽:
 *   default  -  表单主体(各页自己的 el-form,校验 / rules 留各页不动)
 *   footer   -  卡片底部那一行(注册页的「已有账号?去登录」)
 *
 * 主题:
 *   所有颜色走 var(--color-*),亮 / 暗两套自动成立
 *   (暗色由 html[data-theme='dark'] 切换,见 styles/theme.css)。
 *
 * 社交登录:
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
</template>

<style scoped>
/* =============================================================
 * 1. 页面底
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
 * 2. 卡片本体
 *
 * 造型语言(来自设计稿):
 *   40px 大圆角 / 25px 35px 内边距 / max-width 350px
 *   多层柔和外阴影(近景 + 远景大气阴影 = 下坠感)
 * 底色用「卡片色 → 页面底色」的极淡渐变:
 *   亮色 #fff → #faf8f3,暗色 #1f1f1f → #1a1a1a
 * 写死 #fff 的话暗色下会变成一块惨白(暗色最刺眼的坑,见 UserSettings 同样处理)。
 * ============================================================= */
.auth-card {
  position: relative;
  z-index: 1;
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
 * 3. 头部
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
 * 4. 表单控件  -  作用到插槽内容,页面里不再重复写
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
 * 5. 社交登录(保留但置灰)
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
 * 6. 底部插槽(注册页的「已有账号?去登录」)
 * ============================================================= */
.auth-foot {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
  color: var(--color-text-muted);
}

/* =============================================================
 * 7. 移动端 ≤600px:圆角收小、内边距收小、卡片撑满可用宽度
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
