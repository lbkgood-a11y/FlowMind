package com.triobase.service.auth.controller;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.auth.*;
import com.triobase.service.auth.service.AuthService;
import com.triobase.service.auth.service.UserService;
import lombok.RequiredArgsConstructor;
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
    public R<List<String>> accessCodes(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        TokenValidateResult result = authService.validate(token);
        if (!result.isValid()) {
            return R.fail(1005, result.getError());
        }
        return R.ok(result.getPermissions());
    }

    @GetMapping("/me")
    public R<Map<String, Object>> currentUser(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        TokenValidateResult result = authService.validate(token);
        if (!result.isValid()) {
            return R.fail(1005, result.getError());
        }
        UserInfoPayload user = userService.findById(result.getUserId());
        Map<String, Object> userInfo = new java.util.HashMap<>();
        userInfo.put("userId", user.getId());
        userInfo.put("username", user.getUsername());
        userInfo.put("realName", user.getUsername());
        userInfo.put("avatar", "");
        userInfo.put("roles", user.getRoles() != null ? user.getRoles() : List.of());
        userInfo.put("homePath", "/dashboard/analytics");
        userInfo.put("desc", "");
        userInfo.put("token", token);
        return R.ok(userInfo);
    }
}
