package com.triobase.common.dto.auth;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserInfoPayload {
    private String id;
    private String tenantId;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private String introduction;
    private Integer status;
    private List<String> roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
