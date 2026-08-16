package com.tmt.TMLibrary.controller;

import com.tmt.TMLibrary.common.Result.PageResult;
import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.dto.UserDeleteRequest;
import com.tmt.TMLibrary.dto.UserPasswordRequest;
import com.tmt.TMLibrary.dto.UserRegisterRequest;
import com.tmt.TMLibrary.dto.UserSearchRequest;
import com.tmt.TMLibrary.dto.UserUpdatedRequest;
import com.tmt.TMLibrary.entity.User;
import com.tmt.TMLibrary.security.UserView;
import com.tmt.TMLibrary.service.UserManagementService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

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

    /** 注册 — POST /api/users */
    @PostMapping("/register")
    public Result<Integer> create(@Valid @RequestBody UserRegisterRequest req) {
        int id = userManagementService.createUser(req);
        return Result.success(id);
    }

    /** 列表查询 — GET /api/users?username=&role=&page=1&size=10 */
    @GetMapping("/list")
    public Result<PageResult<User>> list(@Valid UserSearchRequest query,
            HttpServletRequest httpReq) {
        UserView me = currentUser(httpReq);
        return Result.success(userManagementService.selectUsersByCriteria(query, me.getRole()));
    }

    /** 详情 — GET /api/users/{id} */
    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable(value = "id") int id) {
        return Result.success(userManagementService.getUserById(id));
    }

    /** 更新 — PATCH /api/users/{id} */
    @PatchMapping("/{id}")
    public Result<Void> update(@PathVariable(value = "id") int id,
            @Valid @RequestBody UserUpdatedRequest req,
            HttpServletRequest httpReq) {
        UserView me = currentUser(httpReq);
        req.setId(id); // URL id 覆盖 body id(防止前端串改)
        userManagementService.updateUser(req, me.getRole(), me.getId());
        return Result.success();
    }

    /** 软删(需密码确认) — DELETE /api/users/{id} */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable int id,
            @Valid @RequestBody UserDeleteRequest req,
            HttpServletRequest httpReq) {
        UserView me = currentUser(httpReq);
        userManagementService.deleteUser(id, req.getPassword(), me.getRole(), me.getId());
        return Result.success();
    }

    /** 改密 — PATCH /api/users/{id}/password */
    @PatchMapping("/{id}/password")
    public Result<Void> changePassword(@PathVariable int id,
            @Valid @RequestBody UserPasswordRequest req,
            HttpServletRequest httpReq) {
        UserView me = currentUser(httpReq);
        userManagementService.changePassword(id, req.getOldPassword(), req.getNewPassword(), me.getId());
        return Result.success();
    }

    /**
     * 临时:从 request attribute 拿当前用户。
     * 明天 JwtAuthFilter 接通后,改成 @CurrentUser UserView me(JwtAuthFilter 会写 attribute)。
     * 现在 fallback 用 BOSS 占位,让 dev 能 curl 测权限链路。
     */
    private UserView currentUser(HttpServletRequest req) {
        UserView me = (UserView) req.getAttribute("CURRENT_USER");
        if (me != null)
            return me;
        // TODO: 明天 JwtAuthFilter 接通后删掉这个 fallback
        me = new UserView();
        me.setId(0);
        me.setRole(UserRole.BOSS); // dev 临时全权限
        return me;
    }
}