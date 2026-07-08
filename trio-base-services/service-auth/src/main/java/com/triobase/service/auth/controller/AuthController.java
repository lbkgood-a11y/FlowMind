package com.triobase.service.auth.controller;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.auth.LoginRequest;
import com.triobase.common.dto.auth.LoginResponse;
import com.triobase.common.dto.auth.LogoutRequest;
import com.triobase.common.dto.auth.TokenValidateResult;
import com.triobase.service.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public R<LoginResponse> register(@RequestParam String username,
                                     @RequestParam String password,
                                     @RequestParam(required = false) String email) {
        return R.ok(authService.register(username, password, email));
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
}
