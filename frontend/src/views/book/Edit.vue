<script setup lang="ts">
/**
 * 新增 / 编辑图书
 *
 * 路由:
 *   /admin/books/new          新增  → POST /api/books/created
 *   /admin/books/:isbn/edit   编辑  → PATCH /api/books/{isbn}
 *
 * 为什么编辑用 isbn 而不是 id 当路由参数:
 *   后端所有写接口(PATCH / DELETE / 详情)都以 isbn 为自然键,只有分页列表才用自增 id。
 *   路由参数跟后端对齐,前端就不用为了"id → isbn"再绕一次接口。
 *
 * 库存字段的特殊处理:
 *   PATCH /api/books/{isbn} 不收 stockQuantity(在途订单有预占,直接 SET 会打架),
 *   所以编辑模式下只有"库存真的被改过"时,才额外打一次 PATCH /{isbn}/stock(盘点语义)。
 */

import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { bookApi } from '@/api/book'
import { useMediaQuery } from '@/composables/useMediaQuery'
import type { BookCategoryNode } from '@/types/api'

const route = useRoute()
const router = useRouter()

/**
 * 手机上把 label 挪到输入框上方 —— 110px 的 label 栏在 375px 屏上要吃掉三分之一宽度,
 * 输入框只剩两百来像素,填 ISBN 这种长内容很难受。
 */
const isNarrow = useMediaQuery('(max-width: 600px)')

/** 有 isbn 参数 = 编辑模式 */
const editingIsbn = computed<string>(() => (typeof route.params.isbn === 'string' ? route.params.isbn : ''))
const isEdit = computed<boolean>(() => editingIsbn.value !== '')

const formRef = ref<FormInstance>()
const submitting = ref<boolean>(false)
const loading = ref<boolean>(false)
const creatingCategory = ref<boolean>(false)
const loadError = ref<string | null>(null)

const form = reactive({
  title: '',
  author: '',
  isbn: '',
  price: 0,
  stockQuantity: 0,
  publishedDate: '',
  /** 选中的大类 id —— 只用于联动小类下拉,不提交给后端 */
  parentId: null as number | null,
  /** 真正提交的分类 id(小类) */
  categoryId: null as number | null,
})

/** 编辑模式下载入的原始库存 —— 用来判断用户到底有没有改过 */
const originalStock = ref<number>(0)

const rules: FormRules = {
  title: [
    { required: true, message: '请输入书名', trigger: 'blur' },
    { max: 200, message: '书名长度不能超过 200', trigger: 'blur' },
  ],
  author: [
    { required: true, message: '请输入作者', trigger: 'blur' },
    { max: 100, message: '作者长度不能超过 100', trigger: 'blur' },
  ],
  isbn: [
    { required: true, message: '请输入 ISBN', trigger: 'blur' },
    // 与后端 BookSaveRequest 的 @Pattern 保持一致:数字 / X / 横杠,10-20 位
    { pattern: /^[0-9Xx-]{10,20}$/, message: 'ISBN 只能是 10-20 位数字、X 或横杠', trigger: 'blur' },
  ],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  stockQuantity: [{ required: true, message: '请输入库存', trigger: 'blur' }],
  publishedDate: [{ required: true, message: '请选择出版日期', trigger: 'change' }],
}

// ============================================================
// 图书类型
// ============================================================

const categories = ref<BookCategoryNode[]>([])

/**
 * 小类下拉的 v-model —— 故意用 `number | string`:
 * el-select 开了 allow-create 后,用户现敲的新名字会**以字符串形式**写进 v-model,
 * 而选已有项时写进来的是数字 id。两种形态都要接住,再在 onSubChange 里分流。
 */
const subModel = ref<number | string | null>(null)
/** 重建 select 用 —— 新建分类成功后 options 换了一批,靠它把 allow-create 造的临时项挤掉 */
const subKey = ref<number>(0)

const subCategories = computed<BookCategoryNode[]>(
  () => categories.value.find((c) => c.id === form.parentId)?.children ?? [],
)

/** 当前选中的分类路径,给用户一个确认(「计算机 / 编程语言」) */
const categoryPath = computed<string>(() => {
  if (!form.parentId) return ''
  const parent = categories.value.find((c) => c.id === form.parentId)
  if (!parent) return ''
  const sub = subCategories.value.find((c) => c.id === form.categoryId)
  return sub ? `${parent.name} / ${sub.name}` : parent.name
})

async function loadCategories(): Promise<void> {
  categories.value = await bookApi.listCategories()
}

/**
 * 切换大类 → 小类必然失效,清掉。
 *
 * flush: 'sync' 是必须的,不能用默认的 pre-flush:
 * applyCategory() 是先写 parentId、再写 categoryId 的(反查出来的小类),
 * 默认时序下 watcher 会等到整个同步块跑完才回调,反而把刚查出来的小类清掉 ——
 * 编辑页打开就变成"未分类"。改成同步触发,watcher 在 parentId 赋值那一刻就跑完,
 * 后面的 categoryId 赋值才是最终状态。
 */
watch(
  () => form.parentId,
  () => {
    form.categoryId = null
    subModel.value = null
  },
  { flush: 'sync' },
)

async function onSubChange(value: unknown): Promise<void> {
  // 清空(clearable)或没选
  if (value === null || value === undefined || value === '') {
    form.categoryId = null
    return
  }
  // 选的是已有小类
  if (typeof value === 'number') {
    form.categoryId = value
    return
  }
  // 敲了个新的 → 先建分类,再挂上
  const name = String(value).trim()
  const parentId = form.parentId
  if (!name) {
    form.categoryId = null
    return
  }
  if (!parentId) {
    ElMessage.warning('请先选择大类')
    subModel.value = null
    return
  }
  // 复用"新建小类"那条路径(含 loading / 报错 / 刷新 / 选中)
  await createSubCategory(parentId, name)
}

/**
 * 弹一个输入框问分类名。
 *
 * <p>用 ElMessageBox.prompt 而不是自己搭一个输入区:它自带校验、回车确认、Esc 取消、
 * 点遮罩关闭,而且窄屏上宽度自适应 —— 手机上比行内输入框好用。</p>
 *
 * @return 用户输入的名字(已 trim);取消或留空返回 null
 */
async function promptCategoryName(title: string, placeholder: string): Promise<string | null> {
  try {
    const { value } = await ElMessageBox.prompt(`请输入${title}名称`, title, {
      confirmButtonText: '创建',
      cancelButtonText: '取消',
      inputPlaceholder: placeholder,
      // 与后端 CategoryCreateRequest 的 @Size(max = 50) 对齐 —— 前端先挡一道,
      // 别让用户敲完 60 个字才被后端拒
      inputValidator: (v: string) => {
        const s = (v ?? '').trim()
        if (!s) return '名称不能为空'
        if (s.length > 50) return '名称不能超过 50 个字符'
        return true
      },
    })
    return value.trim()
  } catch {
    // 点取消 / 关闭 → ElMessageBox reject,这里当"用户放弃"
    return null
  }
}

/** 新建大类 —— 用于下拉里没有用户想要的大类时 */
async function onCreateParent(): Promise<void> {
  const name = await promptCategoryName('新建大类', '例如:自然科学')
  if (!name) return

  creatingCategory.value = true
  try {
    // parentId 传 0 = 新建顶层大类(后端约定,见 CategoryCreateRequest)
    const created = await bookApi.createCategory({ parentId: 0, name })
    await loadCategories()
    form.parentId = created.id
    // 新大类下面还没有小类,把小类清掉避免残留上一个选择
    form.categoryId = null
    subModel.value = null
    ElMessage.success(`已新建大类「${created.name}」`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '新建大类失败')
  } finally {
    creatingCategory.value = false
  }
}

/** 新建小类 —— 给"新建小类"按钮用;allow-create 现敲那条路径也复用这里 */
async function createSubCategory(parentId: number, name: string): Promise<void> {
  creatingCategory.value = true
  try {
    const created = await bookApi.createCategory({ parentId, name })
    await loadCategories()
    form.categoryId = created.id
    subModel.value = created.id
    // 重建 select:allow-create 会往选项列表里塞一个"字符串形态"的临时项,
    // 刷新 options 后靠换 key 把它挤掉
    subKey.value += 1
    ElMessage.success(`已新建分类「${created.name}」`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '新建分类失败')
    subModel.value = null
    form.categoryId = null
  } finally {
    creatingCategory.value = false
  }
}

/** 「没有想要的小类?新建小类」按钮 */
async function onCreateSub(): Promise<void> {
  const parentId = form.parentId
  if (!parentId) {
    ElMessage.warning('请先选择大类,再新建小类')
    return
  }
  const name = await promptCategoryName('新建小类', '例如:科普读物')
  if (!name) return
  await createSubCategory(parentId, name)
}

// ============================================================
// 载入 / 提交
// ============================================================

/** 把后端返回的 categoryId 反查成「大类 + 小类」两个下拉的选中态 */
function applyCategory(categoryId: number | null): void {
  form.parentId = null
  form.categoryId = null
  subModel.value = null
  if (!categoryId) return

  // 情况一:直接挂在大类上(目前录入路径都要求选小类,但历史数据可能这样)
  if (categories.value.some((c) => c.id === categoryId)) {
    form.parentId = categoryId
    return
  }
  // 情况二:挂在小类上 —— 反查它的父
  const parent = categories.value.find((c) => (c.children ?? []).some((s) => s.id === categoryId))
  if (parent) {
    form.parentId = parent.id
    form.categoryId = categoryId
    subModel.value = categoryId
  }
  // 情况三:分类已被删/不存在 → 保持未分类,不阻断编辑
}

async function loadBook(isbn: string): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const book = await bookApi.getByIsbn(isbn)
    if (!book) {
      loadError.value = `未找到 ISBN 为 ${isbn} 的图书`
      return
    }
    form.title = book.title
    form.author = book.author
    form.isbn = book.isbn
    form.price = book.price
    form.stockQuantity = book.stockQuantity ?? 0
    form.publishedDate = book.publishedDate ?? ''
    originalStock.value = book.stockQuantity ?? 0
    applyCategory(book.categoryId)
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '加载图书失败'
  } finally {
    loading.value = false
  }
}

async function onSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (isEdit.value) {
      await bookApi.updateByIsbn(editingIsbn.value, {
        title: form.title,
        author: form.author,
        price: form.price,
        publishedDate: form.publishedDate,
        // undefined = 不传该字段 = 保持原分类。后端也不支持把分类置空(见 BookMapper.xml 的 <if>)
        categoryId: form.categoryId ?? undefined,
      })
      // 库存走独立接口,且只在真的改过时才打
      if (form.stockQuantity !== originalStock.value) {
        await bookApi.adjustStock(editingIsbn.value, { stockQuantity: form.stockQuantity })
      }
      ElMessage.success('已保存修改')
    } else {
      await bookApi.create({
        title: form.title,
        author: form.author,
        isbn: form.isbn,
        price: form.price,
        stockQuantity: form.stockQuantity,
        publishedDate: form.publishedDate,
        categoryId: form.categoryId ?? null,
      })
      ElMessage.success('图书已新增')
    }
    router.replace('/admin/books')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    await loadCategories()
  } catch (e) {
    // 分类拉不到不该挡住录书 —— 提示一下,表单照样能提交(分类留空)
    ElMessage.warning(e instanceof Error ? `分类加载失败:${e.message}` : '分类加载失败')
  }
  if (isEdit.value) await loadBook(editingIsbn.value)
  else form.isbn = ''
})
</script>

<template>
  <div class="book-edit">
    <header class="page-head">
      <div>
        <h1 class="page-title">{{ isEdit ? '编辑图书' : '新增图书' }}</h1>
        <p class="page-sub">
          {{ isEdit ? '修改后立即生效,商城与列表同步更新' : '填好基本信息并选择图书类型,提交后即可在商城看到' }}
        </p>
      </div>
      <div class="head-actions">
        <el-button @click="router.replace('/admin/books')">
          <el-icon><Back /></el-icon>
          <span>返回列表</span>
        </el-button>
      </div>
    </header>

    <div v-if="loadError" class="error-banner" role="alert">
      <el-icon><CircleClose /></el-icon>
      <span>{{ loadError }}</span>
    </div>

    <div class="form-card">
      <el-skeleton v-if="loading" :rows="6" animated />

      <el-form
        v-else
        ref="formRef"
        :model="form"
        :rules="rules"
        :label-width="isNarrow ? 'auto' : '110px'"
        :label-position="isNarrow ? 'top' : 'right'"
        class="book-form"
      >
        <section class="form-section">
          <h2 class="section-title">基本信息</h2>

          <el-form-item label="书名" prop="title">
            <el-input v-model="form.title" placeholder="请输入书名" maxlength="200" show-word-limit />
          </el-form-item>

          <el-form-item label="作者" prop="author">
            <el-input v-model="form.author" placeholder="请输入作者" maxlength="100" />
          </el-form-item>

          <el-form-item label="ISBN" prop="isbn">
            <!-- 编辑模式:ISBN 是后端写接口的键,不给改(要换 ISBN 等于换一本书) -->
            <el-input
              v-model="form.isbn"
              :disabled="isEdit"
              placeholder="10-20 位,可含 X 与横杠,如 978-7-115-42802-8"
              class="mono-input"
            />
            <p v-if="isEdit" class="field-tip">ISBN 是图书的唯一标识,不可修改</p>
          </el-form-item>

          <div class="field-row">
            <el-form-item label="价格" prop="price">
              <el-input-number
                v-model="form.price"
                :min="0"
                :precision="2"
                :step="1"
                :controls="false"
                class="num-input"
              />
            </el-form-item>

            <el-form-item label="库存" prop="stockQuantity">
              <el-input-number
                v-model="form.stockQuantity"
                :min="0"
                :precision="0"
                :step="1"
                :controls="false"
                class="num-input"
              />
            </el-form-item>
          </div>

          <el-form-item label="出版日期" prop="publishedDate">
            <el-date-picker
              v-model="form.publishedDate"
              type="date"
              placeholder="选择出版日期"
              value-format="YYYY-MM-DD"
              class="date-input"
            />
          </el-form-item>
        </section>

        <el-divider />

        <section class="form-section">
          <h2 class="section-title">
            图书类型
            <span class="section-hint">选到小类;输入框里敲新名字可以直接新建</span>
          </h2>

          <el-form-item label="大类">
            <el-select
              v-model="form.parentId"
              clearable
              placeholder="请选择大类"
              class="cat-select"
            >
              <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id">
                <span class="opt-name">{{ c.name }}</span>
                <span class="opt-count">{{ c.bookCount }} 本</span>
              </el-option>
            </el-select>
            <!-- 做成选择框下面的文字按钮,而不是藏在下拉最后一项:
                 下拉里的"新建"要滚到底才看得见,手机上还容易被键盘挡住 -->
            <p class="field-tip">
              没有想要的大类?
              <button
                type="button"
                class="link-btn"
                :disabled="creatingCategory"
                @click="onCreateParent"
              >
                <el-icon><Plus /></el-icon>
                新建大类
              </button>
            </p>
          </el-form-item>

          <el-form-item label="小类">
            <el-select
              :key="subKey"
              v-model="subModel"
              filterable
              allow-create
              default-first-option
              clearable
              :disabled="!form.parentId"
              :loading="creatingCategory"
              :placeholder="form.parentId ? '选择小类,或输入新分类名后回车新建' : '请先选择大类'"
              class="cat-select"
              @change="onSubChange"
            >
              <el-option v-for="c in subCategories" :key="c.id" :label="c.name" :value="c.id">
                <span class="opt-name">{{ c.name }}</span>
                <span class="opt-count">{{ c.bookCount }} 本</span>
              </el-option>
            </el-select>

            <p class="field-tip">
              没有想要的小类?
              <button
                type="button"
                class="link-btn"
                :disabled="creatingCategory || !form.parentId"
                @click="onCreateSub"
              >
                <el-icon><Plus /></el-icon>
                新建小类
              </button>
              <span v-if="!form.parentId" class="tip-hint">(先选大类)</span>
            </p>

            <p v-if="categoryPath" class="field-tip is-ok">
              <el-icon><Collection /></el-icon>
              已选分类:{{ categoryPath }}
            </p>
            <p v-else class="field-tip">不选分类也可以提交,之后可在编辑页补上</p>
          </el-form-item>
        </section>

        <div class="form-actions">
          <el-button type="primary" :loading="submitting" @click="onSubmit">
            {{ isEdit ? '保存修改' : '新增图书' }}
          </el-button>
          <el-button :disabled="submitting" @click="router.replace('/admin/books')">取消</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.book-edit {
  max-width: 880px;
  margin: 0 auto;
  -webkit-font-smoothing: antialiased;
}

/* ============================================================
 * 页头  -  与 book/List 等 admin 页保持一致
 * ============================================================ */
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
  gap: 16px;
  flex-wrap: wrap;
}

.page-title {
  font-family: 'DM Serif Display', Georgia, serif;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.02em;
  margin: 0 0 4px;
  color: var(--color-text);
  text-wrap: balance;
}

.page-sub {
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0;
}

.head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 18px;
  background: rgba(245, 108, 108, 0.08);
  color: #f56c6c;
  border-radius: 10px;
  margin-bottom: 16px;
  font-size: 14px;
}

/* ============================================================
 * 表单卡片
 * ============================================================ */
.form-card {
  background: var(--color-card);
  border-radius: 14px;
  padding: 28px 32px 8px;
  box-shadow:
    0 1px 2px var(--color-shadow),
    0 0 0 1px var(--color-border);
}

.book-form {
  max-width: 600px;
}

.section-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-family: 'Manrope', system-ui, sans-serif;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-soft);
  margin: 0 0 18px;
}

.section-hint {
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0;
  text-transform: none;
  color: var(--color-text-muted);
}

/* 价格 + 库存并排,省一屏高度 */
.field-row {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}

.field-row :deep(.el-form-item) {
  flex: 1 1 220px;
  margin-right: 0;
}

.num-input,
.date-input,
.cat-select {
  width: 100%;
}

/* ISBN —— 连排数字加字距,和列表页的 cell-mono 一个处理 */
.mono-input :deep(.el-input__inner) {
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, system-ui, sans-serif;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.05em;
}

.field-tip {
  display: flex;
  align-items: center;
  gap: 5px;
  width: 100%;
  margin: 6px 0 0;
  font-size: 12.5px;
  line-height: 1.5;
  color: var(--color-text-muted);
}

.field-tip.is-ok {
  color: var(--color-accent);
  font-weight: 500;
}

/* 「新建大类 / 新建小类」文字按钮 —— 做成链接样式,别抢主按钮的注意力 */
.link-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  /* 加大点击热区:文字只有 12.5px,手机上直接点很容易点空 */
  padding: 4px 6px;
  margin: -4px 0;
  background: none;
  border: none;
  border-radius: 6px;
  font-family: inherit;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--color-accent);
  cursor: pointer;
  transition: background-color 180ms ease, color 180ms ease;
}

.link-btn:hover:not(:disabled) {
  background: rgba(76, 175, 80, 0.10);
}

.link-btn:disabled {
  color: var(--color-text-soft);
  cursor: not-allowed;
  opacity: 0.6;
}

.link-btn .el-icon {
  font-size: 13px;
}

.tip-hint {
  color: var(--color-text-soft);
}

/* 下拉项:名字靠左,数量靠右 */
.opt-name {
  float: left;
}

.opt-count {
  float: right;
  font-size: 12px;
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.form-actions {
  display: flex;
  gap: 10px;
  padding: 4px 0 24px;
}

@media (max-width: 768px) {
  .page-title {
    font-size: 24px;
  }

  .form-card {
    padding: 20px 16px 4px;
  }
}
</style>
