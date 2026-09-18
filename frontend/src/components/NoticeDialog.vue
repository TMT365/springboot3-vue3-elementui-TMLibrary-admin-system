<script setup lang="ts">
/**
 * NoticeDialog  -  「浏览须知」登录后弹窗
 *
 * 挂在 App.vue 上,由 useNoticeStore 驱动(链路:Login.vue 登录成功 →
 * setLogin → noticeStore.open() → 本组件)。
 *
 * 为什么是「登录后」而不是「进首页时」:
 *   首页(/)是公开页,访客也能看,在那儿弹等于对还没账号的人也念一遍规矩;
 *   而须知的每一条(下单锁库存、IP 风控、账号不得共享)都以"你已经是这里的
 *   用户"为前提。登录成功那一刻是唯一既明确又只发生一次的时间点。
 *
 * 视觉:
 *   - 居中(align-center)、min(620px, 92vw)
 *   - 顶部徽章 + serif 标题,和 IpBanDialog 同一套语言(它是红的,这里是绿的)
 *   - 正文是编号条目:数字徽章 + 条目标题 + 一句解释
 *   - 条目超出高度时**弹窗内部**滚动,不把整个弹窗顶出视口
 *
 * 主题:颜色全走 var(--color-*),亮 / 暗两套自动成立。
 */

import { computed } from 'vue'
import { useNoticeStore } from '@/stores/notice'

const store = useNoticeStore()

const visible = computed({
  get: () => store.visible,
  set: (v: boolean) => {
    if (!v) store.close()
  },
})

interface NoticeItem {
  /** 条目标题 */
  title: string
  /** 一句话解释 —— 每条只讲一件事,长了没人读 */
  desc: string
}

/**
 * 须知条目。
 *
 * 每一条都对应系统里**真实存在**的机制,不写"请文明上网"这类放之四海皆准的空话:
 *   1. 密码 BCrypt  -  后端 UserServiceImpl 存的是 BCrypt 摘要
 *   2. 下单锁库存  -  PurchaseService 下单即扣库存,取消 / 超时释放
 *   3. 演示数据    -  图书 / 订单均为种子数据(scripts/seed-1000-books.sql)
 *   4. IP 风控     -  RateLimitFilter 触发后前端弹 IpBanDialog(带解封倒计时)
 *   5. 反馈工单    -  右下角 FeedbackFab 提交,管理员在 /feedback 处理
 * 以后后端改了规则,改这里就行,模板不用动。
 */
const ITEMS: NoticeItem[] = [
  {
    title: '账号与身份',
    desc: '一人一号,请勿共享账号或代他人登录。密码经 BCrypt 加密存储,任何情况下我们都不会向你索要密码。',
  },
  {
    title: '借阅与订单',
    desc: '下单即锁定库存,取消或超时未支付会自动释放。订单状态与下单时的价格快照可在「我的订单」随时回看。',
  },
  {
    title: '数据与隐私',
    desc: '本项目为学习 / 演示用途,图书与订单均为种子数据,不涉及真实交易;邮箱、手机号仅用于账号找回。',
  },
  {
    title: '访问规范',
    desc: '系统带 IP 风控:短时间内高频请求会触发临时封禁并弹出解封倒计时。请勿压测、爬取或批量注册。',
  },
  {
    title: '反馈与内容',
    desc: '遇到问题可通过右下角反馈入口提交工单,请就事论事;广告、人身攻击与违法内容会被管理员处理。',
  },
]

/** 版本 / 更新日期 —— 改过条目就顺手改这里,用户能看出"这份须知更新过" */
const VERSION = 'v1.0'
const UPDATED_AT = '2026-09'
</script>

<template>
  <el-dialog
    v-model="visible"
    width="min(620px, 92vw)"
    align-center
    :close-on-click-modal="false"
    class="notice-dialog"
    aria-label="浏览须知"
  >
    <div class="notice-body">
      <!-- 顶部徽章:和 IpBanDialog 的警示徽章同构(这里换成书 + 绿色) -->
      <div class="notice-badge" aria-hidden="true">
        <el-icon :size="30"><Notebook /></el-icon>
      </div>

      <h2 class="notice-title">浏览须知</h2>
      <p class="notice-sub">开始之前,花一分钟看看这里的几条约定</p>

      <ol class="notice-list">
        <li v-for="(item, i) in ITEMS" :key="item.title" class="notice-item">
          <span class="item-no" aria-hidden="true">{{ i + 1 }}</span>
          <div class="item-text">
            <p class="item-title">{{ item.title }}</p>
            <p class="item-desc">{{ item.desc }}</p>
          </div>
        </li>
      </ol>

      <p class="notice-meta">
        {{ VERSION }} · 更新于 {{ UPDATED_AT }} · 每次登录都会展示一次
      </p>
    </div>

    <template #footer>
      <el-button type="primary" size="large" class="notice-ok" @click="store.close()">
        我已阅读
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.notice-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

/* ============================================================
 * 徽章 —— 书 + 一圈缓慢扩散的光环
 *
 * 比 IpBanDialog 的警示徽章安静得多:那个要"心跳"(2.6s),
 * 这个只是"呼吸"(4.2s),不抢正文的注意力。
 * 配色走 CSS 变量,亮暗共用同一组 keyframes;全局的
 * prefers-reduced-motion 规则会把它压到 0.01ms,无障碍自动覆盖。
 * ============================================================ */
.notice-badge {
  /* 徽章底色 / 光晕 / 光环三个色都从这里取 —— 暗色只需覆盖这三个变量,
     keyframes 不用写第二遍(和 IpBanDialog 的 --badge-* 同一套路) */
  --badge-bg: rgba(76, 175, 80, 0.14);
  --badge-glow: rgba(76, 175, 80, 0.12);
  --badge-ring: rgba(76, 175, 80, 0.45);
  position: relative;
  width: 64px;
  height: 64px;
  display: grid;
  place-items: center;
  margin-bottom: 18px;
  border-radius: 50%;
  background: var(--badge-bg);
  color: var(--color-accent);
  animation: notice-breathe 4.2s ease-in-out infinite;
}

.notice-badge::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 1.5px solid var(--badge-ring);
  pointer-events: none;
  animation: notice-ripple 4.2s ease-out infinite;
}

@keyframes notice-breathe {
  0%,
  100% {
    transform: scale(1);
    box-shadow: 0 0 0 6px var(--badge-glow);
  }
  50% {
    transform: scale(1.04);
    box-shadow: 0 0 0 12px var(--badge-glow);
  }
}

@keyframes notice-ripple {
  0% {
    transform: scale(1);
    opacity: 0.8;
  }
  70%,
  100% {
    transform: scale(1.5);
    opacity: 0;
  }
}

.notice-title {
  margin: 0 0 8px;
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 27px;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--color-text);
}

.notice-sub {
  margin: 0 0 22px;
  font-size: 13.5px;
  line-height: 1.6;
  color: var(--color-text-muted);
}

/* ============================================================
 * 条目列表
 *
 * 为什么给 max-height + 内部滚动:条目以后还会加(接了新功能就该补一条),
 * 而且手机上弹窗本来就矮。让它自己滚,弹窗的标题和底部按钮永远可见 ——
 * 否则用户会滚到一个"没有确认按钮"的弹窗里。
 * ============================================================ */
.notice-list {
  width: 100%;
  /* 5 条全展开约 490px(条目内边距 11px / 标题两行)—— 桌面上一屏放得下
     就不要让它滚:滚动区里"刚好被切掉半条"最难读,用户也看不出下面还有内容。
     矮屏(笔记本横屏 / 手机)才落到 60vh,由它自己滚。 */
  max-height: min(60vh, 520px);
  overflow-y: auto;
  margin: 0;
  padding: 4px 2px;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
  text-align: left;
  /* 滚动条本身很细,别在内容右侧留一条白缝 */
  scrollbar-width: thin;
}

.notice-item {
  display: flex;
  gap: 12px;
  padding: 11px 14px;
  border-radius: 14px;
  background: var(--color-bg-alt);
  box-shadow: 0 0 0 1px var(--color-border);
}

/* 编号:和主色同族的淡绿底 + 主色数字 */
.item-no {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: rgba(76, 175, 80, 0.14);
  color: var(--color-accent);
  font-size: 12.5px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.item-text {
  min-width: 0;
}

.item-title {
  margin: 1px 0 3px;
  font-size: 13.5px;
  font-weight: 700;
  color: var(--color-text);
}

.item-desc {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.75;
  color: var(--color-text-muted);
}

.notice-meta {
  margin: 16px 0 0;
  font-size: 11.5px;
  letter-spacing: 0.04em;
  color: var(--color-text-soft);
}

.notice-ok {
  min-width: 160px;
  border: none;
  border-radius: 20px;
  font-weight: 600;
  letter-spacing: 0.02em;
  background: linear-gradient(
    135deg,
    var(--color-accent) 0%,
    var(--color-accent-hover) 100%
  );
  box-shadow: 0 10px 24px -8px rgba(76, 175, 80, 0.65);
}

.notice-ok:hover {
  filter: brightness(1.05);
  box-shadow: 0 14px 28px -8px rgba(76, 175, 80, 0.7);
}

/* ============================================================
 * 暗色适配
 *
 * 亮色那套 rgba(76,175,80,.14) 的淡绿底在深底上会"发闷"(绿色被黑吃掉),
 * 换成亮一档的 #66bb6a 并提高浓度;条目底板 --color-bg-alt(#212121)与
 * 弹窗卡片(#1f1f1f)几乎同色,提亮到 #262626 才立得住。
 * ============================================================ */
:root[data-theme='dark'] .notice-badge {
  --badge-bg: rgba(102, 187, 106, 0.18);
  --badge-glow: rgba(102, 187, 106, 0.16);
  --badge-ring: rgba(102, 187, 106, 0.50);
}

:root[data-theme='dark'] .notice-item {
  background: #262626;
  box-shadow: 0 0 0 1px #343434;
}

:root[data-theme='dark'] .item-no {
  background: rgba(102, 187, 106, 0.20);
}

/* 深底上 soft(#757575)糊成一片,提到 muted(#bdbdbd) */
:root[data-theme='dark'] .notice-meta {
  color: var(--color-text-muted);
}

/* 窄屏:徽章和标题收一档,条目内边距收紧 */
@media (max-width: 480px) {
  .notice-badge {
    width: 54px;
    height: 54px;
    margin-bottom: 14px;
  }

  .notice-title {
    font-size: 22px;
  }

  .notice-sub {
    margin-bottom: 16px;
  }

  .notice-item {
    padding: 10px 12px;
  }

  .notice-list {
    max-height: 52vh;
  }
}
</style>
