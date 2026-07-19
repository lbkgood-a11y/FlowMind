package com.triobase.service.auth.controller;

import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.result.R;
import com.triobase.common.dto.auth.*;
import com.triobase.service.auth.dto.ChangePasswordRequest;
import com.triobase.service.auth.dto.UpdateProfileRequest;
import com.triobase.service.auth.dto.UserProfileResponse;
import com.triobase.service.auth.service.AuthService;
import com.triobase.service.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    public R<LoginResponse> register(@RequestParam String username,
                                     @RequestParam String password,
                                     @RequestParam(required = false) String email,
                                     @RequestParam(required = false) String phone) {
        return R.ok(authService.register(username, password, email, phone));
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@RequestParam String refreshToken) {
        return R.ok(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestBody LogoutRequest request) {
        authService.logout(request.getAccessToken(), request.getRefreshToken());
        return R.ok();
    }

    @GetMapping("/validate")
    public R<TokenValidateResult> validate(@RequestParam String token) {
        return R.ok(authService.validate(token));
    }

    @GetMapping("/codes")
    public R<List<String>> accessCodes(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractBearerToken(authHeader);
        if (!StringUtils.hasText(token)) {
            return R.fail(AuthErrorCode.TOKEN_INVALID);
        }
        TokenValidateResult result = authService.validate(token);
        if (!result.isValid()) {
            return R.fail(1005, result.getError());
        }
        return R.ok(result.getPermissions());
    }

    @GetMapping("/me")
    public R<Map<String, Object>> currentUser(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = extractBearerToken(authHeader);
        if (!StringUtils.hasText(token)) {
            return R.fail(AuthErrorCode.TOKEN_INVALID);
        }
        TokenValidateResult result = authService.validate(token);
        if (!result.isValid()) {
            return R.fail(1005, result.getError());
        }
        UserProfileResponse user = userService.findProfile(result.getUserId());
        Map<String, Object> userInfo = buildUserInfo(user);
        userInfo.put("token", token);
        return R.ok(userInfo);
    }

    @GetMapping("/profile")
    public R<UserProfileResponse> profile(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        return R.ok(userService.findProfile(resolveCurrentUserId(authHeader)));
    }

    @PutMapping("/profile")
    public R<UserProfileResponse> updateProfile(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                                @RequestBody UpdateProfileRequest request) {
        return R.ok(userService.updateProfile(resolveCurrentUserId(authHeader), request));
    }

    @PutMapping("/profile/password")
    public R<Void> changePassword(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                  @RequestBody ChangePasswordRequest request) {
        userService.changePassword(resolveCurrentUserId(authHeader), request);
        return R.ok();
    }

    private String extractBearerToken(String authHeader) {
        if (!StringUtils.hasText(authHeader)) {
            return "";
        }
        if (authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        return authHeader.trim();
    }

    private String resolveCurrentUserId(String authHeader) {
        String userId = SecurityContextHolder.getUserId();
        if (StringUtils.hasText(userId)) {
            return userId;
        }
        String token = extractBearerToken(authHeader);
        if (!StringUtils.hasText(token)) {
            throw new BizException(AuthErrorCode.TOKEN_INVALID);
        }
        TokenValidateResult result = authService.validate(token);
        if (!result.isValid()) {
            throw new BizException(1005, result.getError());
        }
        return result.getUserId();
    }

    private Map<String, Object> buildUserInfo(UserProfileResponse user) {
        Map<String, Object> userInfo = new java.util.HashMap<>();
        userInfo.put("userId", user.getUserId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getRealName());
        userInfo.put("email", user.getEmail());
        userInfo.put("phone", user.getPhone());
        userInfo.put("avatar", user.getAvatar() != null ? user.getAvatar() : "");
        userInfo.put("roles", user.getRoles() != null ? user.getRoles() : List.of());
        userInfo.put("homePath", user.getHomePath());
        userInfo.put("desc", user.getDesc() != null ? user.getDesc() : "");
        userInfo.put("introduction", user.getIntroduction());
        userInfo.put("status", user.getStatus());
        return userInfo;
    }
}
