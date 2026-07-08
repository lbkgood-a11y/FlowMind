package com.triobase.common.core.exception;

public enum AuthErrorCode implements ErrorCode {

    USER_ALREADY_EXISTS(1001, "用户名已存在"),
    BAD_CREDENTIALS(1002, "用户名或密码错误"),
    ACCOUNT_DISABLED(1003, "账户已被禁用"),
    TOKEN_EXPIRED(1004, "Token 已过期，请重新登录"),
    TOKEN_INVALID(1005, "Token 无效"),
    PASSWORD_TOO_WEAK(1006, "密码强度不足，至少 8 位且包含大小写字母和数字"),
    USER_NOT_FOUND(1007, "用户不存在"),
    ROLE_NOT_FOUND(1008, "角色不存在"),
    PERMISSION_DENIED(1009, "权限不足");

    private final int code;
    private final String message;

    AuthErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
