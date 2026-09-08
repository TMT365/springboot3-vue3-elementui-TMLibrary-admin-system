package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.mapper.BookMapper;
import com.tmt.TMLibrary.vo.UserVo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.dto.request.UserRegisterRequest;
import com.tmt.TMLibrary.dto.request.UserSearchRequest;
import com.tmt.TMLibrary.dto.request.UserUpdatedRequest;
import com.tmt.TMLibrary.service.UserManagementService;

import lombok.RequiredArgsConstructor;

import com.tmt.TMLibrary.mapper.UserMapper; // 假设有一个UserMapper用于数据库操作
import com.tmt.TMLibrary.entity.User; // 假设有一个User实体类
import com.tmt.TMLibrary.exception.BusinessException;

import org.springframework.security.crypto.password.PasswordEncoder;
import com.tmt.TMLibrary.common.User.UserStatus; // 导入UserStatus枚举类
import com.tmt.TMLibrary.common.Result.PageResult; // 导入PageResult类
import com.tmt.TMLibrary.common.Result.ResultCode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {
    private final PasswordEncoder passwordEncoder; // 假设有一个PasswordEncoder用于密码加密
    private final UserMapper userMapper; // 假设有一个UserMapper用于数据库操作

    private final BookMapper bookMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_USERS_INFO_BY_USERNAME_PATH = "tmlibrary:user:users:";
    private static final String REDIS_USER_STATUS_BY_ID_PATH = "tmlibrary:user:users:status:";
    private final ObjectMapper objectMapper;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createUser(UserRegisterRequest userRegisterRequest) {
        // 实现创建(注册)用户的逻辑
        // 首先，必须把DTO对象转换为实体类对象，然后调用UserMapper的insertUser方法将用户信息插入数据库
        User user = new User();
        user.setUsername(userRegisterRequest.getUsername());
        // 强制role 是 USER, 其他角色只能老板在后期提升
        user.setRole(UserRole.USER.getCode());
        user.setCreatedTime(java.time.LocalDateTime.now()); 
        user.setPasswordHash(passwordEncoder.encode(userRegisterRequest.getPassword()));
        user.setEmail(userRegisterRequest.getEmail());
        user.setPhoneNumber(userRegisterRequest.getPhoneNumber());
        user.setStatus(UserStatus.ACTIVE.getCode()); // 默认状态为激活

        userMapper.insertUser(user);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUser(int targetUserId, String password, Integer currentRole, Integer currentUserId) {
        // 先查询数据库
        User target = userMapper.selectUserById(targetUserId);
        if (target == null || target.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 1. 密码校验 — 反枚举:用户不存在 / 密码错 同消息,防止用户名枚举
        if (!passwordEncoder.matches(password, target.getPasswordHash())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "密码错误或用户不存在");
        }
        // 2. 权限:自己 OR 角色更高(同等级非自己不行)
        boolean isSelf = currentUserId != null && currentUserId.equals(targetUserId);
        try {
            if (!isSelf && currentRole <= target.getRole()) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权删除该用户");
            }
        }catch (NullPointerException e) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前用户角色信息缺失，无法判断权限");
        }
        // 3. 软删
        target.setDeletedAt(java.time.LocalDateTime.now());
        userMapper.updateUserById(targetUserId, target);

        // 4. 删除 Redis 缓存(按 username,和 login 路径保持同一 key)
        stringRedisTemplate.delete(REDIS_USERS_INFO_BY_USERNAME_PATH + target.getUsername());
        // 同时清掉 status 缓存(PurchaseServiceImpl.checkUser 用)
        stringRedisTemplate.delete(REDIS_USER_STATUS_BY_ID_PATH + targetUserId);

        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageResult<UserVo> selectUsersByCriteria(UserSearchRequest userSearchRequest, Integer role) {

        // 首先，调用 userSearchRequest.compact() 方法压缩查询条件，去除空值和无效值
        if (userSearchRequest == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "User search request cannot be null");
        }
        userSearchRequest.compact();


        // 实现查询用户的逻辑，模糊查询，分页返回
        // 使用了compact()方法来压缩查询条件，去除空值和无效值，这样可以避免在查询时传入无效的参数，从而提高查询效率。
        // 这里的查询条件是通过 userSearchRequest 对象传入的，role 是当前操作用户的角色，用于判断是否有权限查询指定的用户信息。老板可以查询所有用户的信息，管理员只能查询普通用户的信息。
        // 查询时是靠Like模糊查询的方式进行的，查询条件可以是用户名、手机号、邮箱等信息。
        // 这里的分页参数是 page 和 size，分别表示当前页码和每页显示的记录数，查询结果是一个 PageResult<User> 对象，包含了总记录数和当前页的用户列表。
        
        if (role.equals(UserRole.USER.getCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "No permission to query any users");
        }
        // userSearchRequest.getRole() 不可能是 null，因为在 UserSearchRequest 中已经添加了 @NotBlank 注解，表示该字段不能为空，如果为空则会抛出异常，所以这里不需要再判断是否为 null。
        if (role.equals(UserRole.ADMIN.getCode()) && userSearchRequest.getRole().equals(UserRole.BOSS.getCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "No permission to query BOSS users");
        }
        // 计算分页的偏移量
        int offset = (userSearchRequest.getPage() - 1) * userSearchRequest.getSize();
        // 调用UserMapper的countUsersByCriteria方法进行统计
        int total= userMapper.countUsersByCriteria(userSearchRequest, role);
        // 调用UserMapper的selectUsersByCriteria方法进行查询
        List<UserVo> users = userMapper.selectUsersByCriteria(userSearchRequest, role, offset, userSearchRequest.getSize())
                .stream()
                .map(UserVo::fromUser)
                .toList();
        // 这里可以根据实际情况返回总记录数，这里假设总记录数为users.size()，实际情况可能需要单独查询总记录数   
        return new PageResult<>(total, users);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateUser(UserUpdatedRequest userUpdateRequest, Integer currentRole, Integer currentUserId) {

        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户ID不能为空");
        }
        User existing = userMapper.selectUserById(userUpdateRequest.getId());
        if (existing == null || existing.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 提前快照旧 username — 后面 setUsername 会改 existing 的 username,缓存清理时要用旧值
        String oldUsername = existing.getUsername();
        // 权限:自己可改;改他人要求 currentRole > existingRole
        try {
            if (existing.getRole() == null) {
                throw new BusinessException(ResultCode.FORBIDDEN, "当前用户角色信息缺失，无法判断权限");
            }
        } catch (NullPointerException e) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前用户角色信息缺失，无法判断权限");
        }
        boolean isSelf = currentUserId != null && currentUserId.equals(userUpdateRequest.getId());
        if (!isSelf && currentRole <= existing.getRole()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权更新该用户");
        }
        // 普通字段 — null/blank 不动
        if (userUpdateRequest.getUsername() != null && !userUpdateRequest.getUsername().isBlank()) {
            existing.setUsername(userUpdateRequest.getUsername());
        }
        if (userUpdateRequest.getEmail() != null && !userUpdateRequest.getEmail().isBlank()) {
            existing.setEmail(userUpdateRequest.getEmail());
        }
        if (userUpdateRequest.getPhoneNumber() != null && !userUpdateRequest.getPhoneNumber().isBlank()) {
            existing.setPhoneNumber(userUpdateRequest.getPhoneNumber());
        }
        if (userUpdateRequest.getStatus() != null && !userUpdateRequest.getStatus().equals(existing.getRole())) {
            existing.setStatus(userUpdateRequest.getStatus());
        }
        // role 改:仅 BOSS
        if (userUpdateRequest.getRole() != null && !userUpdateRequest.getRole().equals(existing.getRole())) {
            if (!currentRole.equals(UserRole.BOSS.getCode())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "仅 BOSS 可修改用户角色");
            }
            existing.setRole(userUpdateRequest.getRole());
        }
        // updateTime 由 DB 的 ON UPDATE CURRENT_TIMESTAMP 自动刷,Service 不设
        userMapper.updateUserById(existing.getId(), existing);
        // 删除 Redis(用快照的旧 username;如果 username 改了,新 username 的 key 也要删)
        stringRedisTemplate.delete(REDIS_USERS_INFO_BY_USERNAME_PATH + oldUsername);
        if (userUpdateRequest.getUsername() != null && !userUpdateRequest.getUsername().equals(oldUsername)) {
            stringRedisTemplate.delete(REDIS_USERS_INFO_BY_USERNAME_PATH + userUpdateRequest.getUsername());
        }
        // 清掉 status 缓存(可能改了 status / role)
        stringRedisTemplate.delete(REDIS_USER_STATUS_BY_ID_PATH + existing.getId());
        // 如果是在有MySql集群的环境下，使用MQ实现主从一致性
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changePassword(int targetUserId, String oldPassword, String newPassword, Integer currentUserId) {

        // 只能改自己的密码
        if (currentUserId == null || currentUserId != targetUserId) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能修改自己的密码");
        }
        User user = userMapper.selectUserById(targetUserId);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "旧密码错误");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null); // 清掉重置 token
        user.setPasswordResetTokenExpiration(null);
        // 先删除缓存
        stringRedisTemplate.delete(REDIS_USERS_INFO_BY_USERNAME_PATH + user.getUsername());
        userMapper.updateUserById(targetUserId, user);
        //
        return 1;
    }

    @Override
    public UserVo getUserById(int id) {
        // admin 后台查用户用,不进登录热路径,直接打 DB
        User user = userMapper.selectUserById(id);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return UserVo.fromUser(user);
    }
}