package com.tmt.TMLibrary.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.tmt.TMLibrary.entity.User;
import com.tmt.TMLibrary.dto.request.UserSearchRequest;
import com.tmt.TMLibrary.common.User.UserRole;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

/**
 * UserMapper接口，用于定义对User实体类的数据库操作方法。
 * 这个接口使用MyBatis的@Mapper注解，表示它是一个MyBatis的映射器接口。MyBatis会为这个接口生成一个实现类，并将其注册到Spring的IOC容器中，以便在需要时进行依赖注入。
 * 
 * @author tmt
 * @version 1.0
 * @since 2026-08-14
 * 
 * @see com.tmt.TMLibrary.entity.User
 * @see com.tmt.TMLibrary.mapper.UserMapper
 * 
 * UserMapper
 * <p> 在这里关于用户的所有操作,且都是要基于UserName+password/email+Verification/phoneNumber+Verification进行验证的 </p>
 * <p> 1. 创建用户 {@link #insertUser(User)} </p>
 * <p> 2. 删除用户 {@link #deleteUserById(int)} </p>
 * <p> 3. 查询用户 {@link #selectUserByUsername(String)} )} </p>
 * <p> 4. 更新用户 {@link #updateUserById(int, User)} (String, String, User)} </p>
 */ 
@Mapper
public interface UserMapper {
    //=====================增加User======================
    int insertUser(User user);

    //=====================删除User======================
    int deleteUserById(@Param("id") int id);
    

    //=====================查询User======================
    User selectUserById(@Param("id") int id);

    /**
     * 按 username 查用户 — AuthService.login 用,JWT 明天接通时直接用
     */
    User selectUserByUsername(@Param("username") String username);

    // ============================================================
    // 密码重置(2026-09)
    // ============================================================

    /**
     * 按邮箱查用户 —— 「忘记密码」入口用。
     *
     * <p><b>返回 List 而不是单个 User</b>:users 表的 email 列
     * <b>没有唯一约束</b>(唯一键在建表脚本里是注释掉的)。真出现一个邮箱
     * 对应多个账号时,调用方必须能察觉并拒绝 —— 返回单个 User 会让人
     * 以为"就是它了",然后把重置链接发给一个共用邮箱,
     * 等于把其中一个账号的重置权交到另一个账号的主人手里。</p>
     */
    List<User> selectUsersByEmail(@Param("email") String email);

    /**
     * 按「重置令牌的 SHA-256 十六进制」查用户。
     *
     * <p>库里存的是哈希不是明文 —— 明文只在邮件链接里出现一次。
     * 这样即使库被拖走,也拿不到能直接用的重置令牌。</p>
     */
    User selectUserByResetToken(@Param("tokenHash") String tokenHash);

    /** 写入重置令牌(存哈希)与过期时间 */
    int setResetToken(@Param("id") int id,
                      @Param("tokenHash") String tokenHash,
                      @Param("expiration") LocalDateTime expiration);

    /**
     * 清空重置令牌 —— 重置成功后调用,保证**一次性**。
     *
     * <p>不能复用 {@link #updateUserById}:它的 SQL 是
     * {@code <if test="xxx != null">} 拼的,null 字段会被跳过,
     * 根本清不掉值。</p>
     */
    int clearResetToken(@Param("id") int id);
    //================模糊分页查询User======================
    // 这里可以定义一个方法用于模糊查询用户信息，并支持分页功能
    // 例如：
    List<User> selectUsersByCriteria(@Param("userSearchRequest") UserSearchRequest userSearchRequest, @Param("role") Integer role, @Param("offset") int offset, @Param("limit") int limit);

    int countUsersByCriteria(@Param("userSearchRequest") UserSearchRequest userSearchRequest, @Param("role") Integer role);
    

    //=====================更新User=====================
    
    int updateUserById(@Param("id") int id, @Param("user") User user); 

    //=======================其他业务逻辑========================
    /**
     * 原子递增失败次数,达到阈值时一并锁定账户(单条 SQL,无读改写竞态)。
     * <p>SQL 用 {@code failed_login_attempts + 1 >= #{threshold}} 作为判断条件,避免
     * 先 SELECT 再 UPDATE 的两阶段提交窗口。</p>
     * <p>返回 1 表示已锁定,0 表示未锁定(继续累积失败次数)。</p>
     */
    int incrementAndMaybeLock(@Param("id") int id,
                              @Param("threshold") int threshold,
                              @Param("lockMinutes") int lockMinutes);

    LocalDateTime getAccountLockedUntilById(@Param("id") int id);

    int resetFailedLoginAttemptsById(@Param("id") int id);

    int setLastLoginTimeById(@Param("id") int id, @Param("lastLoginTime") LocalDateTime lastLoginTime);

    int setLastLoginIpById(@Param("id") int id, @Param("lastLoginIp") String lastLoginIp);
}
