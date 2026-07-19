package com.triobase.service.auth.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserProfileResponse {
    private String id;
    private String userId;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private String introduction;
    private String desc;
    private Integer status;
    private List<String> roles;
    private String homePath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
