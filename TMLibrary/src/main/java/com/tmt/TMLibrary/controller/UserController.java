package com.tmt.TMLibrary.controller;

import com.tmt.TMLibrary.common.Result.PageResult;
import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.dto.UserDeleteRequest;
import com.tmt.TMLibrary.dto.UserPasswordRequest;
import com.tmt.TMLibrary.dto.PurchaseResponse;
import com.tmt.TMLibrary.dto.UserRegisterRequest;
import com.tmt.TMLibrary.dto.UserSearchRequest;
import com.tmt.TMLibrary.dto.UserUpdatedRequest;
import com.tmt.TMLibrary.entity.User;
import com.tmt.TMLibrary.exception.AuthException;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.security.CurrentUser;
import com.tmt.TMLibrary.security.UserView;
import com.tmt.TMLibrary.service.PurchaseService;
import com.tmt.TMLibrary.service.UserManagementService;

import jakarta.validation.Valid;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.tmt.TMLibrary.dto.LoginResponse;
import com.tmt.TMLibrary.service.AuthService;
import com.tmt.TMLibrary.dto.LoginRequest;

/**
 * @brief 用户管理 RESTful Controller。
 *
 *        <p>
 *        端点:
 * 
 *        <pre>
 *   POST   /api/users/register                  — 注册
 *   GET    /api/users/list                  — 列表查询(ADMIN/BOSS)
 *   GET    /api/users/{id}             — 详情
 *   PATCH  /api/users/{id}             — 更新用户信息
 *   DELETE /api/users/{id}             — 软删(body: password)
 *   PATCH  /api/users/{id}/password    — 改密(只能改自己)
 *        </pre>
 *
 *        <p>
 *        当前用户从 request attribute "CURRENT_USER" 读 — 明天 JwtAuthFilter 接通后自动写入。
 *        现在临时用 BOSS 占位(dev 环境随便测)。
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserManagementService userManagementService;
    private final AuthService authService;
    private final PurchaseService purchaseService;

    /** 注册 — POST /api/users */
    @PostMapping("/register")
    public Result<Integer> create(@Valid @RequestBody UserRegisterRequest req) {
        log.info("前端请求/api/users/register, 参数=username={}, email={}, phoneNumber={}, password=***",
                req.getUsername(), req.getEmail(), req.getPhoneNumber());
        int id = userManagementService.createUser(req);
        return Result.success(id);
    }

    /** 登录 - POST /api/users/login */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        log.info("前端请求/api/users/login, 参数={}", req);
        LoginResponse response = authService.login(req);
        return Result.success(response);
    }

    /** 列表查询 — GET /api/users?username=&role=&page=1&size=10 */
    @GetMapping("/list")
    public Result<PageResult<User>> list(@Valid UserSearchRequest query,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求/api/users/list, 参数={}", query);
        return Result.success(userManagementService.selectUsersByCriteria(query, me.getRole()));
    }

    /** 详情 — GET /api/users/{id} */
    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable(value = "id") int id) {
        log.info("前端请求/api/users/{}", id);
        return Result.success(userManagementService.getUserById(id));
    }

    /** 更新 — PATCH /api/users/{id} */
    @PatchMapping("/{id}")
    public Result<Void> update(@PathVariable(value = "id") int id,
            @Valid @RequestBody UserUpdatedRequest req,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求/api/users/{}, 参数={}", id, req);
        req.setId(id); // URL id 覆盖 body id(防止前端串改)
        userManagementService.updateUser(req, UserRole.getUserRoleByCode(me.getRole()), me.getId());
        return Result.success();
    }

    /** 软删(需密码确认) — DELETE /api/users/{id} */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable int id,
            @Valid @RequestBody UserDeleteRequest req,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求/api/users/{}, password=***", id);
        userManagementService.deleteUser(id, req.getPassword(), UserRole.getUserRoleByCode(me.getRole()), me.getId());
        return Result.success();
    }

    /** 改密 — PATCH /api/users/{id}/password */
    @PatchMapping("/{id}/password")
    public Result<Void> changePassword(@PathVariable int id,
            @Valid @RequestBody UserPasswordRequest req,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求/api/users/{}/password, password=***", id);
        userManagementService.changePassword(id, req.getOldPassword(), req.getNewPassword(), me.getId());
        return Result.success();
    }

    /**
     * 用户的订单列表 — GET /api/users/{id}/purchases
     * 路径 {id} 是被查看的用户 ID。看自己的订单,或 BOSS 看任意人的。
     */
    @GetMapping("/{id}/purchases")
    public Result<List<PurchaseResponse>> listPurchases(@PathVariable int id,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求/api/users/{}/purchases", id);
        if (id != me.getId() && !me.getRole().equals(UserRole.BOSS.getCode())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "Not authorized to view other user's orders");
        }
        List<PurchaseResponse> orders = purchaseService.listOrdersByUserId(id).stream()
                .map(PurchaseResponse::from)
                .collect(java.util.stream.Collectors.toList());
        return Result.success(orders);
    }

    /**
     * 防御兜底 — Filter 没写 attribute 时(null)抛 401。
     * 正常路径下 Filter 一定写了 attribute,所以这个 null 实际上不应该出现。
     */
    private void requireLogin(UserView me) {
        if (me == null) {
            throw new AuthException(ResultCode.UNAUTHORIZED, "未登录");
        }
    }
}