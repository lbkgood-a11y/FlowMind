package com.triobase.common.dto.auth;

import lombok.Data;

import java.util.List;

@Data
public class TokenValidateResult {
    private boolean valid;
    private String userId;
    private String username;
    private String tenantId;
    private List<String> roles;
    private List<String> permissions;
    private Long authVersion;
    private Long roleVersion;
    private Long dataPolicyVersion;
    private String error;

    public static TokenValidateResult success(String userId, String username, List<String> permissions) {
        return success(userId, username, null, null, permissions, null, null, null);
    }

    public static TokenValidateResult success(String userId,
                                              String username,
                                              String tenantId,
                                              List<String> roles,
                                              List<String> permissions,
                                              Long authVersion,
                                              Long roleVersion,
                                              Long dataPolicyVersion) {
        TokenValidateResult r = new TokenValidateResult();
        r.valid = true;
        r.userId = userId;
        r.username = username;
        r.tenantId = tenantId;
        r.roles = roles;
        r.permissions = permissions;
        r.authVersion = authVersion;
        r.roleVersion = roleVersion;
        r.dataPolicyVersion = dataPolicyVersion;
        return r;
    }

    public static TokenValidateResult fail(String error) {
        TokenValidateResult r = new TokenValidateResult();
        r.valid = false;
        r.error = error;
        return r;
    }
}
