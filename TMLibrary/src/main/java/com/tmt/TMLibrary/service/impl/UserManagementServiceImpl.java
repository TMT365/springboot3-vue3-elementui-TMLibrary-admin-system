package com.tmt.TMLibrary.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.dto.UserRegisterRequest;
import com.tmt.TMLibrary.dto.UserSearchRequest;
import com.tmt.TMLibrary.dto.UserUpdatedRequest;
import com.tmt.TMLibrary.service.UserManagementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.tmt.TMLibrary.mapper.UserMapper; // 假设有一个UserMapper用于数据库操作
import com.tmt.TMLibrary.entity.User; // 假设有一个User实体类
import com.tmt.TMLibrary.exception.BusinessException;

import org.springframework.security.crypto.password.PasswordEncoder;
import com.tmt.TMLibrary.common.User.UserStatus; // 导入UserStatus枚举类
import com.tmt.TMLibrary.common.Result.PageResult; // 导入PageResult类
import com.tmt.TMLibrary.common.Result.ResultCode;

import java.util.List;

import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {
    private final PasswordEncoder passwordEncoder; // 假设有一个PasswordEncoder用于密码加密
    private final UserMapper userMapper; // 假设有一个UserMapper用于数据库操作


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
        log.info("用户注册成功: username={}, id={}", user.getUsername(), user.getId());
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteUser(int targetUserId, String password, UserRole currentRole, Integer currentUserId) {
        String where = "[" + this.getClass().getName() +"]"+ ".deleteUser";
        User target = userMapper.selectUserById(targetUserId);
        if (target == null || target.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在", where);
        }
        // 1. 密码校验 — 反枚举:用户不存在 / 密码错 同消息,防止用户名枚举
        if (!passwordEncoder.matches(password, target.getPassword())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "密码错误或用户不存在", where);
        }
        // 2. 权限:自己 OR 角色更高(同等级非自己不行)
        boolean isSelf = currentUserId != null && currentUserId == targetUserId;
        try {
            if (!isSelf && currentRole.getCode() <= target.getRole()) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权删除该用户", where);
            }
        }catch (NullPointerException e) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前用户角色信息缺失，无法判断权限", where);
        }
        
        // 3. 软删
        target.setDeletedAt(java.time.LocalDateTime.now());
        userMapper.updateUserById(targetUserId, target);
        log.info("用户软删: target={}, by={}", targetUserId, currentUserId);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageResult<User> selectUsersByCriteria(UserSearchRequest userSearchRequest, Integer role) {
        String where = "[" + this.getClass().getName() +"]"+ ".selectUsersByCriteria";
        // 首先，调用 userSearchRequest.compact() 方法压缩查询条件，去除空值和无效值
        if (userSearchRequest == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "User search request cannot be null", where);
        }
        userSearchRequest.compact();


        // 实现查询用户的逻辑，模糊查询，分页返回
        // 使用了compact()方法来压缩查询条件，去除空值和无效值，这样可以避免在查询时传入无效的参数，从而提高查询效率。
        // 这里的查询条件是通过 userSearchRequest 对象传入的，role 是当前操作用户的角色，用于判断是否有权限查询指定的用户信息。老板可以查询所有用户的信息，管理员只能查询普通用户的信息。
        // 查询时是靠Like模糊查询的方式进行的，查询条件可以是用户名、手机号、邮箱等信息。
        // 这里的分页参数是 page 和 size，分别表示当前页码和每页显示的记录数，查询结果是一个 PageResult<User> 对象，包含了总记录数和当前页的用户列表。
        
        if (role.equals(UserRole.USER.getCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "No permission to query any users", where);
        }
        // userSearchRequest.getRole() 不可能是 null，因为在 UserSearchRequest 中已经添加了 @NotBlank 注解，表示该字段不能为空，如果为空则会抛出异常，所以这里不需要再判断是否为 null。
        if (role.equals(UserRole.ADMIN.getCode()) && userSearchRequest.getRole() == UserRole.BOSS) {
            throw new BusinessException(ResultCode.FORBIDDEN, "No permission to query BOSS users", where);
        }
        // 计算分页的偏移量
        int offset = (userSearchRequest.getPage() - 1) * userSearchRequest.getSize();
        // 调用UserMapper的countUsersByCriteria方法进行统计
        int total= userMapper.countUsersByCriteria(userSearchRequest, UserRole.getUserRoleByCode(role));
        // 调用UserMapper的selectUsersByCriteria方法进行查询
        List<User> users = userMapper.selectUsersByCriteria(userSearchRequest, UserRole.getUserRoleByCode(role), offset, userSearchRequest.getSize());
        // 这里可以根据实际情况返回总记录数，这里假设总记录数为users.size()，实际情况可能需要单独查询总记录数   
        return new PageResult<>(total, users);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateUser(UserUpdatedRequest userUpdateRequest, UserRole currentRole, Integer currentUserId) {
        String where = "[" + this.getClass().getName() +"]"+ ".updateUser";
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户ID不能为空", where);
        }
        User existing = userMapper.selectUserById(userUpdateRequest.getId());
        if (existing == null || existing.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在", where);
        }
        // 权限:自己可改;改他人要求 currentRole > existingRole
        try {
            if (existing.getRole() == null) {
                throw new BusinessException(ResultCode.FORBIDDEN, "当前用户角色信息缺失，无法判断权限", where);
            }
        } catch (NullPointerException e) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前用户角色信息缺失，无法判断权限", where);
        }
        boolean isSelf = currentUserId != null && currentUserId == userUpdateRequest.getId();
        if (!isSelf && currentRole.getCode() <= existing.getRole()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权更新该用户", where);
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
        if (userUpdateRequest.getStatus() != null && userUpdateRequest.getStatus().getCode() != existing.getStatus()) {
            existing.setStatus(userUpdateRequest.getStatus().getCode());
        }
        // role 改:仅 BOSS
        if (userUpdateRequest.getRole() != null && userUpdateRequest.getRole().getCode() != existing.getRole()) {
            if (currentRole != UserRole.BOSS) {
                throw new BusinessException(ResultCode.FORBIDDEN, "仅 BOSS 可修改用户角色");
            }
            existing.setRole(userUpdateRequest.getRole().getCode());
        }
        // updateTime 由 DB 的 ON UPDATE CURRENT_TIMESTAMP 自动刷,Service 不设
        userMapper.updateUserById(existing.getId(), existing);
        log.info("用户更新: id={}, by={}", userUpdateRequest.getId(), currentUserId);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int changePassword(int targetUserId, String oldPassword, String newPassword, Integer currentUserId) {
        String where = "[" + this.getClass().getName() +"]"+ ".changePassword";
        // 只能改自己的密码
        if (currentUserId == null || currentUserId != targetUserId) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能修改自己的密码", where);
        }
        User user = userMapper.selectUserById(targetUserId);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在", where);
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "旧密码错误", where);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null); // 清掉重置 token
        user.setPasswordResetTokenExpiration(null);
        userMapper.updateUserById(targetUserId, user);
        log.info("用户改密: id={}", targetUserId);
        return 1;
    }

    @Override
    public User getUserById(int id) {
        User user = userMapper.selectUserById(id);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在", "[" + this.getClass().getName() +"]"+ ".getUserById");
        }
        return user;
    }
}