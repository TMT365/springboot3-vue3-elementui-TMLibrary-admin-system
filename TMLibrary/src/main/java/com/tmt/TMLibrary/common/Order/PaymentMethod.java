package com.tmt.TMLibrary.common.Order;

import com.tmt.TMLibrary.common.Result.ResultCode;
import com.tmt.TMLibrary.exception.BusinessException;
import lombok.Getter;

/**
 * 支付方式。
 *
 * <h2>为什么要有这个枚举,而不是直接把前端传的字符串塞进 DB</h2>
 * {@code paymentMethod} 是**客户端可控**的入参。直接落库的话:
 * <ul>
 *   <li>前端传 {@code "随便什么"} 都会写进去,数据库里会出现一堆脏值,
 *       以后按支付方式出对账报表时没法用;</li>
 *   <li>字段长度不受控,超长字符串可能被 MySQL 截断或直接报错(取决于 sql_mode),
 *       表现成"支付失败但订单状态已经改了"这种最难查的半成功状态。</li>
 * </ul>
 * 所以入口处就收敛成枚举:认识的值放行,不认识的直接 400。
 *
 * <h2>和真实支付网关的关系</h2>
 * 目前只是**记录用户选了什么**,没有接任何真实网关 ——
 * 真正的接入要在 {@code payOrder} 之前插一步"调网关 + 验签",
 * 验签通过才允许翻订单状态。本枚举的 {@code code} 就是将来传给网关的那个标识。
 */
@Getter
public enum PaymentMethod {

    WECHAT("WECHAT", "微信支付"),
    ALIPAY("ALIPAY", "支付宝"),
    QQ("QQ", "QQ 钱包");

    /** 落库 / 前后端传输用的标识(大写,与枚举名一致) */
    private final String code;
    private final String description;

    PaymentMethod(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 解析支付方式 —— 大小写不敏感,前后空白忽略。
     *
     * <p>不敏感是刻意的:前端传 {@code wechat} 还是 {@code WECHAT} 属于调用方风格问题,
     * 没必要因此让用户付款失败。但**不认识的值必须拒绝**,不能默默回退成默认值 ——
     * 那会让"传错了"表现成"付成功了但方式不对",比直接报错难查得多。</p>
     *
     * @throws BusinessException 传了空值或无法识别的值(400)
     */
    public static PaymentMethod from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "支付方式不能为空");
        }
        String normalized = raw.trim().toUpperCase();
        for (PaymentMethod m : values()) {
            if (m.code.equals(normalized)) {
                return m;
            }
        }
        throw new BusinessException(ResultCode.BAD_REQUEST,
            "不支持的支付方式: " + raw + "(可选:WECHAT / ALIPAY / QQ)");
    }
}
