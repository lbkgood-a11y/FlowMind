package com.triobase.service.auth.controller;

import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.result.R;
import com.triobase.common.dto.auth.*;
import com.triobase.service.auth.dto.ChangePasswordRequest;
import com.triobase.service.auth.dto.RegisterRequest;
import com.triobase.service.auth.dto.UpdateProfileRequest;
import com.triobase.service.auth.dto.UserProfileResponse;
import com.triobase.service.auth.service.AuthService;
import com.triobase.service.auth.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REGISTER_RATE_KEY_PREFIX = "register:rate:";
    private static final int MAX_REGISTRATIONS_PER_HOUR = 3;
    private static final Duration REGISTER_RATE_WINDOW = Duration.ofHours(1);

    private final AuthService authService;
    private final UserService userService;
    private final StringRedisTemplate redis;

    @PostMapping("/register")
    public R<LoginResponse> register(@RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        String rateKey = REGISTER_RATE_KEY_PREFIX + clientIp;
        Boolean isNew = redis.opsForValue().setIfAbsent(rateKey, "1", REGISTER_RATE_WINDOW);
        if (Boolean.TRUE.equals(isNew)) {
            return R.ok(authService.register(request.getUsername(), request.getPassword(),
                    request.getEmail(), request.getPhone()));
        }
        Long count = redis.opsForValue().increment(rateKey);
        if (count != null && count > MAX_REGISTRATIONS_PER_HOUR) {
            throw new BizException(42900, "REGISTER_RATE_LIMITED");
        }
        return R.ok(authService.register(request.getUsername(), request.getPassword(),
                request.getEmail(), request.getPhone()));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/login")
    public R<LoginResponse> login(@RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public R<LoginResponse> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (!StringUtils.hasText(refreshToken)) {
            throw new BizException(AuthErrorCode.TOKEN_INVALID);
        }
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
        return R.ok(buildUserInfo(user));
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
