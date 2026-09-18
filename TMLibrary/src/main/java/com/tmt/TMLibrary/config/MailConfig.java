package com.tmt.TMLibrary.config;

import com.tmt.TMLibrary.service.MailService;
import com.tmt.TMLibrary.service.impl.LoggingMailService;
import com.tmt.TMLibrary.service.impl.SmtpMailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 选择邮件实现:有 SMTP 就真发,没有就写日志。
 *
 * <h2>为什么用 ObjectProvider 而不是 @ConditionalOnBean</h2>
 * <p>{@code @ConditionalOnBean} 的求值时机依赖自动配置的注册顺序 ——
 * 在用户自定义的 {@code @Configuration} 里用它判断一个由自动配置提供的 Bean,
 * 结果是不确定的(官方文档专门警告过这一点,顺序不对就会永远判为 false)。
 * {@link ObjectProvider} 是运行时查询,没有这个时序问题。</p>
 *
 * <p>注意 {@code MailSenderAutoConfiguration} 本身带
 * {@code @ConditionalOnProperty("spring.mail.host")} —— 没配 SMTP 主机时
 * JavaMailSender 这个 Bean 压根不存在,这里的判断自然落到日志实现。</p>
 */
@Slf4j
@Configuration
public class MailConfig {

    @Bean
    public MailService mailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                                   @org.springframework.beans.factory.annotation.Value("${app.mail.from:no-reply@tmlibrary.local}") String from) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("未配置 spring.mail.host —— 密码重置邮件不会真正发送,重置链接将打印到日志");
            return new LoggingMailService();
        }
        log.info("已启用 SMTP 邮件发送: from={}", from);
        return new SmtpMailService(sender, from);
    }
}
