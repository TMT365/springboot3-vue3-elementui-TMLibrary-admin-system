package com.tmt.TMLibrary.controller;

import com.tmt.TMLibrary.common.Result.PageResult;
import com.tmt.TMLibrary.common.Result.Result;
import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.common.User.UserRole;
import com.tmt.TMLibrary.common.utils.IpUtil;
import com.tmt.TMLibrary.dto.request.ForgotPasswordRequest;
import com.tmt.TMLibrary.dto.request.ResetPasswordRequest;
import com.tmt.TMLibrary.dto.request.UserDeleteRequest;
import com.tmt.TMLibrary.dto.request.UserPasswordRequest;
import com.tmt.TMLibrary.dto.response.PurchaseResponse;
import com.tmt.TMLibrary.dto.request.UserRegisterRequest;
import com.tmt.TMLibrary.dto.request.UserSearchRequest;
import com.tmt.TMLibrary.dto.request.UserUpdatedRequest;
import com.tmt.TMLibrary.vo.UserVo;
import com.tmt.TMLibrary.exception.AuthException;
import com.tmt.TMLibrary.exception.BusinessException;
import com.tmt.TMLibrary.security.context.CurrentUser;
import com.tmt.TMLibrary.security.context.UserView;
import com.tmt.TMLibrary.service.PasswordResetService;
import com.tmt.TMLibrary.service.PurchaseService;
import com.tmt.TMLibrary.service.UserManagementService;

import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import com.tmt.TMLibrary.dto.response.LogoutResponse;
import com.tmt.TMLibrary.dto.response.LoginResponse;
import com.tmt.TMLibrary.service.AuthService;
import com.tmt.TMLibrary.dto.request.LoginRequest;

/**
 * 用户管理 RESTful Controller。
 *
 *        <p>
 *        端点:
 *
 *        <pre>
 *   POST   /users/register                  — 注册
 *   POST   /users/login                     — 登录
 *   POST   /users/logout                    — 登出
 *   POST   /users/forgot-password           — 申请密码重置(公开,永远返回成功)
 *   POST   /users/reset-password            — 凭令牌重置密码(公开)
 *   GET    /users/list                      — 列表查询(ADMIN/BOSS)
 *   GET    /users/{id}                      — 详情
 *   PATCH  /users/{id}                      — 更新用户信息
 *   DELETE /users/{id}                      — 软删(body: password)
 *   PATCH  /users/{id}/password             — 改密(只能改自己)
 *   GET    /users/{id}/purchases            — 看订单(自己 or BOSS)
 *        </pre>
 *
 *        <p>
 *        当前用户从 request attribute "CURRENT_USER" 读 — JwtAuthFilter 写入,
 *        &#64;CurrentUser UserView me 注入到方法参数。
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserManagementService userManagementService;
    private final AuthService authService;
    private final PurchaseService purchaseService;
    private final PasswordResetService passwordResetService;

    /** 注册 — POST /users */
    @PostMapping("/register")
    public Result<Integer> create(@Valid @RequestBody UserRegisterRequest req) {
        log.info("前端请求/users/register, 参数 username={}, email={}, phoneNumber={}, password=***",
                req.getUsername(), req.getEmail(), req.getPhoneNumber());
        int id = userManagementService.createUser(req);
        return Result.success(id);
    }

    // ============================================================
    // 密码重置(2026-09)
    //
    // 两个都是**公开端点**(已加进 JwtAuthFilter 白名单)—— 用户正是
    // 因为登不上去才要用它们,不可能要求先带 token。
    // ============================================================

    /**
     * 申请密码重置 — POST /users/forgot-password
     *
     * <p><b>永远返回成功</b>,不管邮箱是否注册过。这是防用户枚举的关键:
     * 一旦"该邮箱未注册"和"已发送"能被区分开,这个接口就成了批量探测
     * 哪些邮箱在本站有账号的工具。Service 内部对三种情况(不存在 /
     * 命中多个账号 / 正常)都做了静默处理,只在日志里区分。</p>
     */
    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        // 邮箱是个人信息,日志里只记长度不记内容
        log.info("前端请求/users/forgot-password, email 长度={}", req.getEmail().length());
        passwordResetService.requestReset(req.getEmail());
        return Result.success();
    }

    /**
     * 凭令牌重置密码 — POST /users/reset-password
     *
     * <p>这个**会返回失败**:令牌无效/过期/用过,或者新密码与原密码相同,
     * 都会返回 400 并带明确原因。用户必须知道改没改成,
     * 否则他以为成功了、下次还用旧密码登录。</p>
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        // 令牌是凭据,不能进日志
        log.info("前端请求/users/reset-password, token 长度={}", req.getToken().length());
        passwordResetService.resetPassword(req.getToken(), req.getNewPassword());
        return Result.success();
    }

    /** 登录 - POST /users/login */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                      HttpServletRequest request) {
        // 客户端 IP 由服务端解析并落库(前端传的不信任)——此前该字段从未被赋值,
        // 导致 last_login_ip 恒为 NULL、"按登录 IP 筛选"永远查不到数据
        req.setIpAddress(IpUtil.resolveClientIp(request));
        log.info("前端请求/users/login, 参数={}", req);
        LoginResponse response = authService.login(req);
        return Result.success(response);
    }

    /** 登出 - POST /users/logout
     * 返回 LogoutResponse,前端据 loggedOut=true 跳转到 /login(已登出) */
    @PostMapping("/logout")
    public Result<LogoutResponse> logout(HttpServletRequest request) {
        log.info("前端发送/users/logout");
        return Result.success(authService.logout(request));
    }

    /** 列表查询 — GET /users/list?username=&amp;role=&amp;page=1&amp;size=10 */
    @GetMapping("/list")
    public Result<PageResult<UserVo>> list(@ModelAttribute @Valid UserSearchRequest query,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求/users/list, 参数={}", query);
        return Result.success(userManagementService.selectUsersByCriteria(query, me.getRole()));
    }

    /** 详情 — GET /users/{id}
     * 仅自己 / ADMIN / BOSS 可看 */
    @GetMapping("/{id}")
    public Result<UserVo> getById(@PathVariable(value = "id") int id,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求/users/{}", id);

        UserVo target = userManagementService.getUserById(id);

        // 权限与「列表查询」保持同一套规则:
        //   自己 → 可看
        //   BOSS → 可看任意
        //   ADMIN → 只能看普通用户(不能看 BOSS / 其他 ADMIN)
        if (me.getId() == id || me.getRole().equals(UserRole.BOSS.getCode())) {
            return Result.success(target);
        }
        if (me.getRole().equals(UserRole.ADMIN.getCode())
                && UserRole.USER.getCode().equals(target.getRole())) {
            return Result.success(target);
        }
        throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该用户详情");
    }

    /** 更新 — PATCH /users/{id} */
    @PatchMapping("/{id}")
    public Result<Void> update(@PathVariable(value = "id") int id,
            @Valid @RequestBody UserUpdatedRequest req,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求/users/{}, 参数={}", id, req);
        req.setId(id); // URL id 覆盖 body id(防止前端串改)
        userManagementService.updateUser(req, me.getRole(), me.getId());
        return Result.success();
    }

    /** 软删(需密码确认) — DELETE /users/{id} */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable int id,
            @Valid @RequestBody UserDeleteRequest req,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求/users/delete/{}, password=***", id);
        userManagementService.deleteUser(id, req.getPassword(), me.getRole(), me.getId());
        return Result.success();
    }

    /** 改密 — PATCH /users/{id}/password */
    @PatchMapping("/{id}/password")
    public Result<Void> changePassword(@PathVariable int id,
            @Valid @RequestBody UserPasswordRequest req,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求/users/{}/password, password=***", id);
        userManagementService.changePassword(id, req.getOldPassword(), req.getNewPassword(), me.getId());
        return Result.success();
    }

    /**
     * 用户的订单列表 — GET /users/{id}/purchases
     * 路径 {id} 是被查看的用户 ID。看自己的订单,或 BOSS 看任意人的。
     */
    @GetMapping("/{id}/purchases")
    public Result<List<PurchaseResponse>> listPurchases(@PathVariable int id,
            @CurrentUser UserView me) {
        requireLogin(me);
        log.info("前端请求/users/{}/purchases", id);
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