package com.tmt.TMLibrary.service;

/**
 * 邮件发送门面。
 *
 * <h2>为什么抽成接口</h2>
 * <p>「忘记密码」需要把重置链接送到用户邮箱。但 SMTP 是外部依赖 ——
 * 本地开发、CI、演示环境都不会配。如果业务代码直接依赖
 * {@code JavaMailSender},这些环境要么起不来,要么整个流程没法自测。</p>
 *
 * <p>所以抽一层:有 SMTP 就真发,没有就把内容打进日志。
 * 两个实现由 {@code MailConfig} 按「容器里有没有 JavaMailSender Bean」来选,
 * 业务侧只认这个接口。</p>
 *
 * <h2>实现方必须遵守</h2>
 * <p><b>发送失败不要往外抛</b>。调用方是「忘记密码」接口,而那个接口
 * 无论邮箱存不存在都要返回同样的成功响应(防用户枚举,见
 * {@code UserManagementServiceImpl.requestPasswordReset})。
 * 如果这里抛异常,调用方要么把它吞掉、要么就会因为邮件服务器抖动
 * 而暴露"这个邮箱是存在的" —— 所以约定在这里内部消化,只记日志。</p>
 */
public interface MailService {

    /**
     * 发送密码重置邮件。
     *
     * @param toEmail  收件地址
     * @param username 收件人用户名(用于正文称呼)
     * @param resetUrl 重置链接(含明文令牌,**不要记进日志以外的任何地方**)
     */
    void sendPasswordReset(String toEmail, String username, String resetUrl);
}
