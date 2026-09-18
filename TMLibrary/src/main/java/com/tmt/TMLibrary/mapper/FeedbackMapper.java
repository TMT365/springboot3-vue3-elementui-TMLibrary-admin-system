package com.tmt.TMLibrary.mapper;

import com.tmt.TMLibrary.dto.response.FeedbackReplyView;
import com.tmt.TMLibrary.dto.response.FeedbackSummary;
import com.tmt.TMLibrary.dto.response.FeedbackView;
import com.tmt.TMLibrary.entity.Feedback;
import com.tmt.TMLibrary.entity.FeedbackReply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FeedbackMapper {

    /** 插入主表 —— 自增主键回写到 {@code feedback.id} */
    int insertFeedback(Feedback feedback);

    /** 插入回复 */
    int insertReply(FeedbackReply reply);

    /** 详情主表(不含 replies) */
    Feedback selectFeedbackById(@Param("id") Long id);

    /**
     * 详情主表(带 username) —— 走 Service 内部建视图时用,因为 entity 不带 username。
     * 命名上把名字收在 resultMap,只回 entity + username 字符串对,
     * 不让 Service 知道"username 是另一条 SQL 取的"还是"JOIN 来的"——细节藏在这里。
     */
    java.util.Map<String, Object> selectFeedbackDetail(@Param("id") Long id);

    /**
     * 单条回复列表(直接返回 DTO,带 username) —— Service 层根据 role 决定要不要过滤 is_internal
     *
     * <p>用 DTO 而不是 entity 是因为 username 在 mapper 里 JOIN 出来,
     * 把 DTO 直接交给 Service 比"先回 entity 再 service 里设 username"少一次循环。</p>
     */
    List<FeedbackReplyView> selectRepliesByFeedbackId(@Param("feedbackId") Long feedbackId);

    /**
     * 列表 + 联 users 表取 username + LEFT JOIN replies 算回复数 —— 一条 SQL 走完,
     * 避免 N+1。
     */
    List<FeedbackSummary> selectFeedbackSummaries(@Param("userId") Integer userId,
                                                @Param("includeAll") boolean includeAll,
                                                @Param("status") Integer statusFilter,
                                                @Param("category") String categoryFilter,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    /**
     * 列表分页总数(同样的过滤条件) —— service 端 PageResult 用
     */
    int countFeedbacks(@Param("userId") Integer userId,
                       @Param("includeAll") boolean includeAll,
                       @Param("status") Integer statusFilter,
                       @Param("category") String categoryFilter);

    /** 改状态/优先级 + 必要时写 resolved_time —— 见 SQL 注释 */
    int updateStatusAndPriority(@Param("id") Long id,
                                @Param("status") Integer status,
                                @Param("priority") Integer priority,
                                @Param("touchResolvedTime") boolean touchResolvedTime);
}
