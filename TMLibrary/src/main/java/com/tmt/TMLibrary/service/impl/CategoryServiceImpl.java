package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.dto.response.BookCategoryNode;
import com.tmt.TMLibrary.entity.BookCategory;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.mapper.BookCategoryMapper;
import com.tmt.TMLibrary.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 图书分类 —— 读路径给商城(免登录),写路径给后台。
 *
 * <h2>缓存策略:Cache-Aside + 写时删除</h2>
 * <p>分类树是典型的<b>读多写极少</b>(每次打开商城/后台表单都要读,几天才改一次),
 * 整棵树缓存成一个 key({@link RedisKeys#BOOK_CATEGORY_TREE})。</p>
 *
 * <p><b>写路径一律"先删缓存,再写 MySQL"</b>(不是"更新缓存"):</p>
 * <ul>
 *   <li>删除是幂等的 —— 两次删除、漏删(靠 TTL 兜底)都不会写出错误数据;
 *       而"更新缓存"在并发写下会把旧值盖在新值上</li>
 *   <li>删除放在 MySQL 写入<b>之前</b>,窗口更短</li>
 *   <li>不做延迟双删:第二次删除要靠延时线程/定时任务兜,复杂度不划算 ——
 *       后续接 MQ(订阅 binlog)做最终一致,由 MQ 消费端负责失效</li>
 * </ul>
 *
 * <p><b>已知残留窗口</b>:删除 Redis 之后、MySQL 事务提交之前,若正好有读请求,
 * 它会把"还没提交的旧树"重新写进缓存,使缓存脏到 TTL 到期。
 * 概率极低(窗口 = 一次 INSERT/UPDATE 的时间),且分类数据不是强一致场景,
 * 先接受;MQ 上线后由消费端在事务提交后统一失效。</p>
 *
 * <p><b>Redis 故障不影响可用性</b>:读写缓存异常一律 log 后回源查库,
 * 跟 {@code StatsServiceImpl} / {@code BookServiceImpl} 一个套路。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    /** 顶层大类的哨兵 parentId(用 0 而非 NULL,见 BookCategory 类注释) */
    private static final int ROOT_PARENT_ID = 0;

    private static final int MAX_NAME_LENGTH = 50;

    /**
     * 分类树缓存 TTL —— 30 分钟。
     * <p>只作兜底:正常路径由写时删除保证新鲜度。这里<b>不加随机偏移</b> ——
     * 全站只有这一个 key,不存在"大量 key 同时过期"的雪崩场景。</p>
     */
    private static final long CACHE_TTL_MINUTES = 30;

    private final BookCategoryMapper categoryMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public List<BookCategoryNode> listTree() {
        // 1. 先查缓存
        List<BookCategoryNode> cached = readTreeCache();
        if (cached != null) {
            return cached;
        }

        // 2. 回源 + 组装
        List<BookCategoryNode> tree = buildTree();

        // 3. 写缓存(失败不影响本次返回)
        writeTreeCache(tree);

        return tree;
    }

    /** 查库组装两级树 —— 大类保持查询顺序,小类第二轮挂上去并把数量累加给父 */
    private List<BookCategoryNode> buildTree() {
        List<BookCategory> all = categoryMapper.selectAll();

        Map<Integer, BookCategoryNode> parentNodes = new LinkedHashMap<>();
        List<BookCategory> childRows = new ArrayList<>();
        for (BookCategory c : all) {
            if (isTopLevel(c)) {
                parentNodes.put(c.getId(), BookCategoryNode.from(c));
            } else {
                childRows.add(c);
            }
        }

        for (BookCategory child : childRows) {
            BookCategoryNode parent = parentNodes.get(child.getParentId());
            if (parent == null) {
                // 父大类被删过,留下孤儿小类 —— 跳过而不是报错,商城不该因为脏数据整个挂掉
                log.warn("分类 {} 的父分类 {} 不存在,已跳过", child.getId(), child.getParentId());
                continue;
            }
            // addChild 里会把子类数量累加到大类上
            parent.addChild(BookCategoryNode.from(child));
        }

        return new ArrayList<>(parentNodes.values());
    }

    /** 读缓存 —— 任何异常(含脏 JSON)都当未命中,回源即可 */
    private List<BookCategoryNode> readTreeCache() {
        try {
            String json = stringRedisTemplate.opsForValue().get(RedisKeys.BOOK_CATEGORY_TREE);
            if (json == null || json.isBlank()) {
                return null;
            }
            // 用数组接:泛型 List 的反序列化要 TypeReference,数组省掉这层且语义一样
            BookCategoryNode[] nodes = objectMapper.readValue(json, BookCategoryNode[].class);
            return Arrays.asList(nodes);
        } catch (Exception e) {
            log.warn("分类树缓存读取失败,回源查库: err={}", e.getMessage());
            return null;
        }
    }

    private void writeTreeCache(List<BookCategoryNode> tree) {
        try {
            String json = objectMapper.writeValueAsString(tree);
            stringRedisTemplate.opsForValue()
                    .set(RedisKeys.BOOK_CATEGORY_TREE, json,
                            Expiration.from(CACHE_TTL_MINUTES, TimeUnit.MINUTES));
        } catch (Exception e) {
            log.warn("分类树缓存写入失败(不影响本次返回): err={}", e.getMessage());
        }
    }

    /**
     * 失效分类树缓存 —— <b>必须在 MySQL 写入之前调用</b>。
     * <p>Redis 删失败只 log:最坏情况是缓存脏到 TTL 到期,不该因为缓存问题让写操作失败。</p>
     */
    private void evictTreeCache(String reason) {
        try {
            Boolean deleted = stringRedisTemplate.delete(RedisKeys.BOOK_CATEGORY_TREE);
            log.debug("分类树缓存失效({}): deleted={}", reason, deleted);
        } catch (Exception e) {
            log.warn("分类树缓存删除失败({}),将依赖 TTL 兜底: err={}", reason, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BookCategory create(Integer parentId, String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分类名不能为空");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "分类名不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }

        int effectiveParentId = (parentId == null) ? ROOT_PARENT_ID : parentId;

        // 挂在小类下面?不允许 —— 只有两级
        if (effectiveParentId != ROOT_PARENT_ID) {
            BookCategory parent = categoryMapper.selectById(effectiveParentId);
            if (parent == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "上级分类不存在");
            }
            if (!isTopLevel(parent)) {
                throw new BusinessException(ResultCode.BAD_REQUEST,
                        "分类最多两级,不能在「" + parent.getName() + "」下再建子分类");
            }
        }

        // 同父下重名:直接复用已有的,不让前端因为重复点击而报错
        BookCategory existing = categoryMapper.selectByParentAndName(effectiveParentId, trimmed);
        if (existing != null) {
            return existing;
        }

        BookCategory category = new BookCategory();
        category.setParentId(effectiveParentId);
        category.setName(trimmed);
        category.setSortOrder(nextSortOrder(effectiveParentId));

        // 先删缓存,再写 MySQL —— 顺序不能反,否则删之前的读会把旧树又写回缓存
        evictTreeCache("新建分类");
        categoryMapper.insert(category);

        log.info("新建图书分类: id={}, parentId={}, name={}",
                category.getId(), effectiveParentId, trimmed);

        // 回查一次:book_count / created_time 这些列是 DB 默认值填的,
        // 只靠 insert 拿到的对象里它们还是 null,直接返回会给前端一个"半成品"
        BookCategory saved = categoryMapper.selectById(category.getId());
        return saved != null ? saved : category;
    }

    @Override
    public void adjustBookCount(Integer categoryId, int delta) {
        if (categoryId == null || delta == 0) {
            return;
        }
        // 计数变了 = 树上挂的数量变了 → 先删缓存再改 MySQL
        // (本方法跑在 BookService 的事务里,提交时机由外层决定 —— 见类注释"已知残留窗口")
        evictTreeCache("图书计数调整 " + delta);

        int rows = categoryMapper.adjustBookCount(categoryId, delta);
        if (rows == 0) {
            // 计数漂移(比如减到负数被 WHERE 挡住)时不影响业务,靠 recountAll 修复
            log.warn("分类 {} 的图书计数未能按 {} 调整(可能已漂移),稍后可用 recountAll 修复",
                    categoryId, delta);
        }
    }

    private boolean isTopLevel(BookCategory c) {
        return c.getParentId() == null || c.getParentId() == ROOT_PARENT_ID;
    }

    /** 追加到同级末尾 —— 该父下已有的最大 sort_order + 1 */
    private int nextSortOrder(int parentId) {
        return categoryMapper.selectAll().stream()
                .filter(c -> c.getParentId() != null && c.getParentId() == parentId)
                .mapToInt(c -> c.getSortOrder() == null ? 0 : c.getSortOrder())
                .max()
                .orElse(0) + 1;
    }
}
