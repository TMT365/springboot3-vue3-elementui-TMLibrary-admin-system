package com.tmt.TMLibrary.service;

import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.dto.UserRegisterRequest;
import com.tmt.TMLibrary.dto.UserSearchRequest;
import com.tmt.TMLibrary.dto.UserUpdatedRequest;
import com.tmt.TMLibrary.entity.User;
import com.tmt.TMLibrary.common.Result.PageResult;

public interface UserManagementService {

    /**
     * 注册用户 — 强制 role=USER,Service 屏蔽自选角色漏洞
     */
    int createUser(UserRegisterRequest req);

    /**
     * 删除用户(软删)
     * @param targetUserId   要删除的目标用户 id
     * @param password       密码确认(自己删自己时校验)
     * @param currentRole    当前操作用户角色
     * @param currentUserId  当前操作用户 id(用于区分自己/他人)
     */
    int deleteUser(int targetUserId, String password, UserRole currentRole, Integer currentUserId);

    /**
     * 搜索用户列表(ADMIN / BOSS)
     */
    PageResult<User> selectUsersByCriteria(UserSearchRequest req, Integer currentRole);

    /**
     * 更新用户信息
     * @param req           更新请求
     * @param currentRole   当前操作用户角色
     * @param currentUserId 当前操作用户 id(用于区分自己/他人)
     */
    int updateUser(UserUpdatedRequest req, UserRole currentRole, Integer currentUserId);

    /**
     * 修改密码 — 必须 currentUserId == targetUserId(只能改自己)。
     * 改他人密码走管理员流程(忘记密码 → 重置 token),不在此接口。
     */
    int changePassword(int targetUserId, String oldPassword, String newPassword, Integer currentUserId);

    /**
     * 获取单个用户(排除软删)
     */
    User getUserById(int id);
}