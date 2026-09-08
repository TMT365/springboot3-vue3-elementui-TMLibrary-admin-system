package com.tmt.TMLibrary.dto.redis;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import com.tmt.TMLibrary.entity.User;

@Getter
@Setter
public class UserRedis {
    private Integer id;
    private String username;
    private String realName;
    private String passwordHash;
    private String phoneNumber;
    private String email;
    private Integer status;
    private Integer role;
    private LocalDateTime deletedAt;

    public static UserRedis fromUser(User user){
        UserRedis ur = new UserRedis();
        ur.setId(user.getId());
        ur.setUsername(user.getUsername());
        ur.setPasswordHash(user.getPasswordHash());
        ur.setPhoneNumber(user.getPhoneNumber());
        ur.setEmail(user.getEmail());
        ur.setStatus(user.getStatus());
        ur.setRole(user.getRole());
        ur.setDeletedAt(user.getDeletedAt());
        ur.setRealName(user.getRealName());
        return ur;
    }
}
