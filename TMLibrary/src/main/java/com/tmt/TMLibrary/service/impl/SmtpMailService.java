package com.tmt.TMLibrary.service.impl;

import com.tmt.TMLibrary.service.MailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 通过 SMTP 真正发信 —— 配了 {@code spring.mail.host} 时的实现。
 *
 * <p>用 {@link SimpleMailMessage} 而不是 MimeMessage:重置邮件就一段纯文本 +
 * 一个链接,不需要 HTML。少了 MIME 组装,也就少了 HTML 邮件常见的
 * 头部注入和渲染差异问题。</p>
 *
 * <p>发送失败在这里被吞掉(见 {@link MailService} 的约定)——
 * 只记 WARN。调用方的响应必须和"邮箱不存在"时完全一致。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpMailService implements MailService {

    private final JavaMailSender mailSender;
    private final String from;

    @Override
    public void sendPasswordReset(String toEmail, String username, String resetUrl) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(toEmail);
        msg.setSubject("【TMLibrary】密码重置");
        msg.setText("""
                %s,你好:

                我们收到了重置 TMLibrary 账号密码的请求。
                请打开下面的链接设置新密码(30 分钟内有效,且只能使用一次):

                %s

                如果这不是你本人的操作,忽略这封邮件即可 —— 你的密码不会被修改。
                """.formatted(username, resetUrl));

        try {
            mailSender.send(msg);
            log.info("密码重置邮件已发送: to={}", toEmail);
        } catch (MailException e) {
            // 内部消化,不往上抛 —— 否则调用方会因为它而改变响应,
            // 变相泄露"这个邮箱确实存在"
            log.warn("密码重置邮件发送失败(不影响接口响应): to={}, err={}", toEmail, e.getMessage());
        }
    }
}
