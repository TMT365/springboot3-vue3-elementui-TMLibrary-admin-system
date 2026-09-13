package com.tmt.TMLibrary.exception;

import com.tmt.TMLibrary.common.Result.ResultCode;

/**
 * 付款时因库存不足导致订单被自动取消。
 *
 * <p>这个异常的特殊之处在于:<b>它必须让事务提交,而不是回滚</b>。</p>
 *
 * <p>触发链路:付款 → 预检发现库存不足 → 在<b>同一个事务内</b>把订单置为 CANCELLED
 * 并释放 Redis 预占 → 抛出本异常。若按默认规则回滚,刚写入的"已取消"状态会被撤销,
 * 订单又回到 PENDING,用户陷入"付不了款也取消不掉"的状态。</p>
 *
 * <p>因此 {@code payOrder} 上标注:
 * {@code @Transactional(rollbackFor = Exception.class, noRollbackFor = OrderAutoCancelledException.class)}
 * — Spring 的规则匹配取"最具体"的那条,本类比 {@code Exception} 更具体,故优先生效。</p>
 */
public class OrderAutoCancelledException extends BusinessException {

    public OrderAutoCancelledException(String message) {
        super(ResultCode.CONFLICT, message);
    }
}
