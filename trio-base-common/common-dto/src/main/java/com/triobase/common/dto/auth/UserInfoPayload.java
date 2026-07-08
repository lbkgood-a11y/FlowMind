package com.triobase.common.dto.auth;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserInfoPayload {
    private String id;
    private String username;
    private String email;
    private String phone;
    private Integer status;
    private List<String> roles;
    private LocalDateTime createdAt;
}
