package com.tmt.TMLibrary.mapper;

import org.apache.ibatis.annotations.Mapper;
import com.tmt.TMLibrary.entity.User;
import com.tmt.TMLibrary.dto.UserSearchRequest;
import com.tmt.TMLibrary.common.User.UserRole;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * @brief UserMapper接口，用于定义对User实体类的数据库操作方法。
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
 * <p> 2. 删除用户 {@link #deleteUserByUserNameAndPassword(String, String)} </p>
 * <p> 3. 查询用户 {@link #selectUserByUserNameAndPassword(String, String)} </p>
 * <p> 4. 更新用户 {@link #updateUserByUserNameAndPassword(String, String, User)} </p>
 */ 
@Mapper
public interface UserMapper {
    //=====================增加User======================
    int insertUser(User user);

    //=====================删除User======================
    int deleteUserByUserNameAndPassword(@Param("userName") String userName, @Param("password") String password);

    int deleteUserById(@Param("id") int id);
    

    //=====================查询User======================
    User selectUserById(@Param("id") int id);

    /**
     * 按 username 查用户 — AuthService.login 用,JWT 明天接通时直接用
     */
    User selectByUsername(@Param("username") String username);
    //================模糊分页查询User======================
    // 这里可以定义一个方法用于模糊查询用户信息，并支持分页功能
    // 例如：
    List<User> selectUsersByCriteria(@Param("userSearchRequest") UserSearchRequest userSearchRequest, @Param("role") UserRole role, @Param("offset") int offset, @Param("limit") int limit);

    int countUsersByCriteria(@Param("userSearchRequest") UserSearchRequest userSearchRequest, @Param("role") UserRole role);
    

    //=====================更新User=====================
    
    int updateUserById(@Param("id") int id, @Param("user") User user);
}
