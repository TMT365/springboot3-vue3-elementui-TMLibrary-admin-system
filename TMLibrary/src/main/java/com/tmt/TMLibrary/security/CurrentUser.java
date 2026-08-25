package com.tmt.TMLibrary.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @brief 标记 Controller 方法参数为"当前登录用户"。
 *        <p>
 *        由 {@link CurrentUserArgumentResolver} 解析,
 *        从 {@link HttpServletRequest#getAttribute(String) request attribute}
 *        "CURRENT_USER" 读出 {@link UserView} 自动注入。
 *
 *        <p>
 *        用法:
 * 
 *        <pre>
 *        &#64;GetMapping("/me")
 *        public Result&lt;UserView&gt; me(&#64;CurrentUser UserView me) {
 *            return Result.success(me); // 已经是当前用户
 *        }
 *        </pre>
 *
 *        <p>
 *        注意:
 *        <ul>
 *        <li>只能标在方法参数上(不能标类/方法)</li>
 *        <li>运行时保留(Spring 反射才能读到)</li>
 *        <li>必须配合 CurrentUserArgumentResolver + CurrentUserContext 使用</li>
 *        </ul>
 */
@Target(ElementType.PARAMETER) // ← 只能用在"参数"位置
@Retention(RetentionPolicy.RUNTIME) // ← 运行时保留(Spring反射要读)
public @interface CurrentUser {
    // 空注解 — 它就是个标记,不需要任何属性 // 真要加属性,可以这样:
    // boolean required() default true; // 是否要求必须登录
}