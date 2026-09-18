package com.tmt.TMLibrary.service;

/**
 * 密码重置(「忘记密码」流程)。
 *
 * <p>拆成独立接口而不是塞进 {@code UserManagementService}:后者的方法
 * 全部要求"操作者已登录"(带 {@code currentUserId} / {@code currentRole} 参数),
 * 而这两个是**公开接口**——没有任何操作者上下文。放在一起会让
 * "这个方法到底要不要鉴权"变得含糊。</p>
 */
public interface PasswordResetService {

    /**
     * 受理重置申请:找到账号就发一封带重置链接的邮件。
     *
     * <h2>无论邮箱是否存在,调用方都必须返回同样的成功响应</h2>
     * <p>本方法内部也遵守这一点:邮箱不存在、命中多个账号、邮件发送失败,
     * <b>一律不抛异常</b>,只在日志里区分。任何差异化的返回(哪怕只是
     * "该邮箱未注册")都会把这个接口变成<b>用户枚举</b>工具 ——
     * 攻击者批量试邮箱就能确认哪些地址在本站有账号。</p>
     *
     * @param email 用户填的邮箱
     */
    void requestReset(String email);

    /**
     * 用令牌设置新密码。
     *
     * <p>这个**会抛**业务异常:令牌无效/过期/已被用过,或者新密码不合法,
     * 用户必须知道 —— 否则他改不成密码却看到"成功",更糟。</p>
     *
     * @param token       邮件链接里的明文令牌
     * @param newPassword 新密码
     */
    void resetPassword(String token, String newPassword);
}
