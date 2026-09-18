package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.service.MailService;
import lombok.extern.slf4j.Slf4j;

/**
 * 不发邮件,把重置链接打进日志 —— 没配 SMTP 时的兜底实现。
 *
 * <p>被 {@code MailConfig} 在「容器里没有 JavaMailSender Bean」时选中,
 * 也就是没设 {@code spring.mail.host} 的场合。</p>
 *
 * <h2>为什么重置链接可以用 WARN 级别打出来</h2>
 * <p>正常业务日志里出现凭据是事故,这里是有意的例外:不这么做的话,
 * 本地开发和演示环境**完全没法走通**忘记密码流程(收不到邮件就等于没有链接)。
 * 用 WARN 而不是 INFO,是为了在日志里显眼、并且在生产环境的日志级别
 * 配置下也不会被 INFO 淹没。</p>
 *
 * <p><b>生产环境请务必配好 SMTP</b> —— 否则任何能看到日志的人都能重置任意账号。</p>
 */
@Slf4j
public class LoggingMailService implements MailService {

    @Override
    public void sendPasswordReset(String toEmail, String username, String resetUrl) {
        log.warn("""

                        ┌────────────────────────── 密码重置链接 ──────────────────────────
                        │ 未配置 SMTP(spring.mail.host 为空),邮件没有真正发出。
                        │ 收件人 : {} ({})
                        │ 链接   : {}
                        │ 有效期 : 30 分钟,且只能使用一次
                        └──────────────────────────────────────────────────────────────────""",
                username, toEmail, resetUrl);
    }
}
