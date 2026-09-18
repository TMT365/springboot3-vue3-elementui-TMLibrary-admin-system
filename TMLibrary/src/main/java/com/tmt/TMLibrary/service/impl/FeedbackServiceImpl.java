package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.common.Result.PageResult;
import com.tmt.TMLibrary.common.redis.RedisKeys;
import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.dto.request.FeedbackCreateRequest;
import com.tmt.TMLibrary.dto.request.FeedbackReplyRequest;
import com.tmt.TMLibrary.dto.request.FeedbackStatusRequest;
import com.tmt.TMLibrary.dto.response.FeedbackReplyView;
import com.tmt.TMLibrary.dto.response.FeedbackSummary;
import com.tmt.TMLibrary.dto.response.FeedbackView;
import com.tmt.TMLibrary.entity.Feedback;
import com.tmt.TMLibrary.entity.FeedbackReply;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.mapper.FeedbackMapper;
import com.tmt.TMLibrary.security.context.UserView;
import com.tmt.TMLibrary.service.FeedbackService;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 反馈工单 Service 实现。
 *
 * <h2>缓存策略</h2>
 * <p><b>只缓存详情,不缓存列表</b>。列表的过滤维度多(userId / status / category / 分页),
 * 缓存键的笛卡尔积会爆炸,命中率也低,直接打 DB。</p>
 *
 * <p>详情缓存分两套 key(普通用户 vs 管理员) —— 因为内部备注要按角色过滤,
 * 同一 id 在不同人眼里内容不同,不能共用一个 key,否则会出现"用户查 → 缓存 →
 * 管理员查 → 拿到的是被过滤过的那份"的串味。</p>
 *
 * <p>写/改路径先删缓存再写 MySQL(同 {@code CategoryServiceImpl} 的策略,不是延迟双删)
 * —— 接 MQ 同步是更远的下一步。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    /** 详情缓存 TTL —— 5 分钟,过了一致性窗口就不再纠结强一致 */
    private static final Duration DETAIL_TTL = Duration.ofMinutes(5);

    private final FeedbackMapper feedbackMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // ============================================================
    // 写路径
    // ============================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserView me, FeedbackCreateRequest req) {
        if (me == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        Feedback fb = new Feedback();
        fb.setUserId(me.getId());
        fb.setCategory(req.getCategory());
        fb.setTitle(req.getTitle());
        fb.setBody(req.getBody());
        fb.setStatus(0);   // OPEN
        fb.setPriority(1); // NORMAL
        feedbackMapper.insertFeedback(fb);
        log.info("提交反馈: id={}, userId={}, category={}", fb.getId(), me.getId(), req.getCategory());
        return fb.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reply(UserView me, Long id, FeedbackReplyRequest req) {
        if (me == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        Feedback fb = mustExist(id);
        boolean isAdmin = isAdminOrBoss(me);
        // 普通用户只能给"自己提的"追述 —— 否则越权看别人工单
        if (!isAdmin && !fb.getUserId().equals(me.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能回复自己的反馈");
        }
        FeedbackReply r = new FeedbackReply();
        r.setFeedbackId(id);
        r.setUserId(me.getId());
        r.setRole(me.getRole());
        // 普通用户传的 isInternal 强制当 false —— 防止有人塞 true 假装内部备注
        r.setIsInternal(isAdmin && Boolean.TRUE.equals(req.getIsInternal()) ? 1 : 0);
        r.setBody(req.getBody());
        feedbackMapper.insertReply(r);
        evictDetailCaches(id);
        log.info("回复反馈: feedbackId={}, userId={}, isInternal={}", id, me.getId(), r.getIsInternal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(UserView me, Long id, FeedbackStatusRequest req) {
        if (me == null || !isAdminOrBoss(me)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可改状态");
        }
        // 至少传一个字段
        if (req.getStatus() == null && req.getPriority() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "status 和 priority 至少传一个");
        }
        // status 改到 RESOLVED(2)/CLOSED(3) 才记 resolved_time,改回 OPEN 时清空
        boolean touchResolved = req.getStatus() != null && (req.getStatus() == 2 || req.getStatus() == 3);
        feedbackMapper.updateStatusAndPriority(id, req.getStatus(), req.getPriority(), touchResolved);
        evictDetailCaches(id);
        log.info("改反馈状态/优先级: id={}, status={}, priority={}", id, req.getStatus(), req.getPriority());
    }

    // ============================================================
    // 读路径
    // ============================================================

    @Override
    public FeedbackView detail(UserView me, Long id) {
        if (me == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        Feedback fb = mustExist(id);
        boolean isAdmin = isAdminOrBoss(me);
        // 普通用户不能看别人的工单(否则绕过"自己看自己"的限制)
        if (!isAdmin && !fb.getUserId().equals(me.getId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能查看自己的反馈");
        }

        // 命中缓存直接返回 —— key 包含 viewerId,管理员/用户不会互窜
        String cacheKey = isAdmin
                ? RedisKeys.feedbackDetailAdmin(id)
                : RedisKeys.feedbackDetail(id, me.getId());
        FeedbackView cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        FeedbackView view = buildView(fb, isAdmin);
        // 详情主表 entity 不带 username,这里补一次联表查 —— 改走 selectFeedbackDetail
        // 比"entity + 单条 SELECT username"少一次 DB 交互
        java.util.Map<String, Object> detailRow = feedbackMapper.selectFeedbackDetail(id);
        if (detailRow != null) {
            view.setUsername((String) detailRow.get("username"));
        }
        writeCache(cacheKey, view);
        return view;
    }

    @Override
    public PageResult<?> myList(UserView me, int page, int size) {
        if (me == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        return listPage(me.getId(), false, null, null, page, size);
    }

    @Override
    public PageResult<?> allList(Integer status, String category, int page, int size) {
        return listPage(null, true, status, category, page, size);
    }

    // ============================================================
    // 私有工具方法
    // ============================================================

    @SuppressWarnings("unchecked")
    private PageResult<FeedbackSummary> listPage(Integer userId, boolean includeAll,
                                                Integer status, String category,
                                                int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        int offset = (safePage - 1) * safeSize;
        List<FeedbackSummary> items = feedbackMapper.selectFeedbackSummaries(
                userId, includeAll, status, category, offset, safeSize);
        int total = feedbackMapper.countFeedbacks(userId, includeAll, status, category);
        return new PageResult<>(total, items);
    }

    private FeedbackView buildView(Feedback fb, boolean includeInternal) {
        FeedbackView v = new FeedbackView();
        v.setId(fb.getId());
        v.setUserId(fb.getUserId());
        v.setCategory(fb.getCategory());
        v.setTitle(fb.getTitle());
        v.setBody(fb.getBody());
        v.setStatus(fb.getStatus());
        v.setPriority(fb.getPriority());
        v.setCreatedTime(fb.getCreatedTime());
        v.setUpdatedTime(fb.getUpdatedTime());
        v.setResolvedTime(fb.getResolvedTime());

        // 列表 SQL 联表取过 username,这里再取一次提单人(轻量 —— 走 Redis 缓存)
        // —— 见 DTO 注释:username 是冗余字段,但前端展示要,JOIN 更省事
        List<FeedbackReplyView> replyViews = feedbackMapper.selectRepliesByFeedbackId(fb.getId()).stream()
                .filter(r -> includeInternal || r.getIsInternal() == null || r.getIsInternal() == 0)
                .collect(Collectors.toList());
        v.setReplies(replyViews);
        return v;
    }

    private Feedback mustExist(Long id) {
        Feedback fb = feedbackMapper.selectFeedbackById(id);
        if (fb == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "反馈不存在: id=" + id);
        }
        return fb;
    }

    private static boolean isAdminOrBoss(UserView me) {
        Integer role = me.getRole();
        return UserRole.ADMIN.getCode().equals(role) || UserRole.BOSS.getCode().equals(role);
    }

    // ============================================================
    // 缓存读写 —— 任何异常当穿透,不让缓存问题阻塞业务
    // ============================================================

    /**
     * 失效某条反馈的**所有**详情缓存。
     *
     * <p><b>为什么不能只删管理员那份</b>:普通用户的 key 里带 viewerId,
     * 每个看过这条反馈的人各有一条。只删管理员那条的话,改完状态普通用户
     * 还会看到旧状态直到 TTL 到期 —— 用户提的"反馈处理进度"页显示的还是三天前的状态。</p>
     *
     * <p>用 SCAN 而不是 KEYS:KEYS 阻塞 Redis 单线程;SCAN 游标分批。
     * key 数量 = 看过这条反馈的人数,量级可控,一次遍历没问题。</p>
     */
    private void evictDetailCaches(Long feedbackId) {
        try {
            Set<String> keys = stringRedisTemplate.execute((RedisConnection connection) -> {
                Set<String> found = new HashSet<>();
                ScanOptions options = ScanOptions.scanOptions()
                        .match(RedisKeys.feedbackDetailPattern(feedbackId))
                        .count(100)
                        .build();
                try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    while (cursor.hasNext()) {
                        found.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                }
                return found;
            });
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.debug("失效反馈详情缓存: id={}, keys={}", feedbackId, keys.size());
            }
        } catch (Exception e) {
            // 缓存删不掉不该让写操作失败 —— 最坏情况是脏到 TTL 到期
            log.warn("反馈缓存失效失败(将依赖 TTL 兜底): id={}, err={}", feedbackId, e.getMessage());
        }
    }

    private FeedbackView readCache(String key) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return null;
            }
            // 用项目里现成的 tools.jackson ObjectMapper —— Spring 自动注入了同名 bean
            return objectMapper.readValue(json, FeedbackView.class);
        } catch (Exception e) {
            log.warn("反馈缓存读取失败,穿透到 DB: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, FeedbackView view) {
        try {
            objectMapper.writeValueAsString(view);
            String json = objectMapper.writeValueAsString(view);
            stringRedisTemplate.opsForValue().set(key, json, DETAIL_TTL.toMinutes(), TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("反馈缓存写入失败(不影响本次返回): key={}, err={}", key, e.getMessage());
        }
    }

}
