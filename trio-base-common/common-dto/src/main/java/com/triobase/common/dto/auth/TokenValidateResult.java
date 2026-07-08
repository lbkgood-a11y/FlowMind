package com.triobase.common.dto.auth;

import lombok.Data;

import java.util.List;

@Data
public class TokenValidateResult {
    private boolean valid;
    private String userId;
    private String username;
    private List<String> permissions;
    private String error;

    public static TokenValidateResult success(String userId, String username, List<String> permissions) {
        TokenValidateResult r = new TokenValidateResult();
        r.valid = true;
        r.userId = userId;
        r.username = username;
        r.permissions = permissions;
        return r;
    }

    public static TokenValidateResult fail(String error) {
        TokenValidateResult r = new TokenValidateResult();
        r.valid = false;
        r.error = error;
        return r;
    }
}
