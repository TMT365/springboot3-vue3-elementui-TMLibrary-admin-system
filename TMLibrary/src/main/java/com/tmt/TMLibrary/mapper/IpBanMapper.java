package com.tmt.TMLibrary.mapper;

import com.tmt.TMLibrary.entity.IpBan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * IP 封禁记录 —— 只在"触发封禁"和"人工解封"时落库,
 * 每请求的判定走 Redis(见 IpBanService)。
 */
@Mapper
public interface IpBanMapper {

    /** 写一条封禁记录 */
    int insertBan(IpBan ban);

    /** 该 IP 最近一条仍生效的封禁(unbanned_at IS NULL AND expires_at > NOW()) */
    IpBan selectActiveByIp(@Param("ip") String ip);

    /** 全部生效中的封禁,按封禁时间倒序 —— 给管理端看 */
    List<IpBan> selectActiveBans();

    /** 人工解封:把未解封的记录标记为已解封,返回影响行数 */
    int unban(@Param("ip") String ip);

    /** 清理早已过期的历史记录(可选的运维动作,保留最近 N 天) */
    int deleteExpiredBefore(@Param("before") java.time.LocalDateTime before);
}
