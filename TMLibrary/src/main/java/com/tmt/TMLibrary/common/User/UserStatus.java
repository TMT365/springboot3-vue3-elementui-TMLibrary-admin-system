package com.tmt.TMLibrary.common.User;

/**
 * @brief 这个类定义了用户状态的枚举类型，用于表示用户账户的不同状态。
 * @author tmt
 * @version 1.0
 * @since 2026-08-14
 * UserStatus
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED
    // ACTIVE表示账户正常，INACTIVE表示账户未激活，SUSPENDED表示账户被暂停，封号
}
