package com.tmt.TMLibrary.dto.redis;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.tmt.TMLibrary.entity.User;
import org.jspecify.annotations.NonNull;

@Getter
@Setter
public class UserStatusRedis {
    private Integer id;
    private Integer status;
    private LocalDateTime deletedAt;

    public static @NonNull UserStatusRedis fromUser(User user) {
        UserStatusRedis us = new UserStatusRedis();
        us.setId(user.getId());
        us.setStatus(user.getStatus());
        us.setDeletedAt(user.getDeletedAt());
        return us;
    }
}