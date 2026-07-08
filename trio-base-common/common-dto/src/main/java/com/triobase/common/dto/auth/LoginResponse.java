package com.triobase.common.dto.auth;

import lombok.Data;

import java.util.List;

@Data
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String userId;
    private String username;
    private List<String> roles;
}
