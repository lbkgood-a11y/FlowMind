package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.jwt.JwtUtil;
import com.triobase.common.dto.auth.LoginRequest;
import com.triobase.common.dto.auth.LoginResponse;
import com.triobase.common.dto.auth.TokenValidateResult;
import com.triobase.service.auth.entity.SysUser;
import com.triobase.service.auth.entity.SysUserRole;
import com.triobase.service.auth.mapper.UserMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_KEY_PREFIX = "refresh:";
    private static final String REVOKED_KEY_PREFIX = "revoked:";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;

    @Value("${auth.jwt.secret}")
    private String jwtSecret;

    @Value("${auth.jwt.access-token-ttl:300}")
    private int accessTokenTtl;

    @Value("${auth.jwt.refresh-token-ttl:1800}")
    private int refreshTokenTtl;

    @Transactional
    public LoginResponse register(String username, String password, String email, String phone) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)) > 0) {
            throw new BizException(AuthErrorCode.USER_ALREADY_EXISTS);
        }
        if (password.length() < 8
                || !password.matches(".*[a-z].*")
                || !password.matches(".*[A-Z].*")
                || !password.matches(".*\\d.*")) {
            throw new BizException(AuthErrorCode.PASSWORD_TOO_WEAK);
        }
        SysUser user = new SysUser();
        user.setId("U" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setPhone(phone);
        user.setStatus(1);
        userMapper.insert(user);

        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId("R003");
        userRoleMapper.insert(userRole);

        log.info("User registered: {} ({})", username, user.getId());
        return buildLoginResponse(user, List.of("USER"));
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(AuthErrorCode.BAD_CREDENTIALS);
        }
        if (user.getStatus() != 1) {
            throw new BizException(AuthErrorCode.ACCOUNT_DISABLED);
        }
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        log.info("User logged in: {} ({})", user.getUsername(), user.getId());
        return buildLoginResponse(user, roles);
    }

    public LoginResponse refresh(String refreshToken) {
        JwtUtil.JwtPayload payload = JwtUtil.verifyAndParse(refreshToken, jwtSecret);
        if (!payload.valid() || !"refresh".equals(payload.type())) {
            throw new BizException(AuthErrorCode.TOKEN_INVALID);
        }
        if (payload.userId() == null || payload.jti() == null) {
            throw new BizException(AuthErrorCode.TOKEN_INVALID);
        }

        String refreshKey = REFRESH_KEY_PREFIX + payload.userId() + ":" + payload.jti();
        String stored = redis.opsForValue().get(refreshKey);
        if (stored == null) {
            log.warn("Refresh token replay detected: userId={}, jti={}", payload.userId(), payload.jti());
            revokeAllUserTokens(payload.userId());
            throw new BizException(AuthErrorCode.TOKEN_INVALID);
        }

        redis.delete(refreshKey);

        SysUser user = userMapper.selectById(payload.userId());
        if (user == null || user.getStatus() != 1) {
            throw new BizException(AuthErrorCode.ACCOUNT_DISABLED);
        }
        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        return buildLoginResponse(user, roles);
    }

    public TokenValidateResult validate(String token) {
        JwtUtil.JwtPayload payload = JwtUtil.verifyAndParse(token, jwtSecret);
        if (!payload.valid() || !"access".equals(payload.type())) {
            return TokenValidateResult.fail(payload.error());
        }
        if (payload.jti() != null && Boolean.TRUE.equals(redis.hasKey(REVOKED_KEY_PREFIX + payload.jti()))) {
            return TokenValidateResult.fail("Token 已被吊销");
        }
        List<String> permissions = userMapper.selectPermissionsByUserId(payload.userId());
        return TokenValidateResult.success(payload.userId(), payload.username(), permissions);
    }

    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null) {
            JwtUtil.JwtPayload accessPayload = JwtUtil.verifyAndParse(accessToken, jwtSecret);
            if (accessPayload.valid() && accessPayload.jti() != null) {
                long remaining = accessPayload.expirationTime() != null
                        ? Math.max(1, (accessPayload.expirationTime().getTime() - System.currentTimeMillis()) / 1000)
                        : accessTokenTtl;
                redis.opsForValue().set(REVOKED_KEY_PREFIX + accessPayload.jti(), "1", Duration.ofSeconds(remaining));
            }
        }
        if (refreshToken != null) {
            JwtUtil.JwtPayload refreshPayload = JwtUtil.verifyAndParse(refreshToken, jwtSecret);
            if (refreshPayload.valid() && refreshPayload.jti() != null && refreshPayload.userId() != null) {
                redis.delete(REFRESH_KEY_PREFIX + refreshPayload.userId() + ":" + refreshPayload.jti());
            }
        }
    }

    private void revokeAllUserTokens(String userId) {
        var keys = redis.keys(REFRESH_KEY_PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private LoginResponse buildLoginResponse(SysUser user, List<String> roles) {
        String accessToken = JwtUtil.createAccessToken(user.getId(), user.getUsername(), roles, jwtSecret, accessTokenTtl);
        String refreshToken = JwtUtil.createRefreshToken(user.getId(), user.getUsername(), jwtSecret, refreshTokenTtl);

        JwtUtil.JwtPayload refreshPayload = JwtUtil.verifyAndParse(refreshToken, jwtSecret);
        if (refreshPayload.valid() && refreshPayload.jti() != null) {
            String refreshKey = REFRESH_KEY_PREFIX + user.getId() + ":" + refreshPayload.jti();
            redis.opsForValue().set(refreshKey, "active", Duration.ofSeconds(refreshTokenTtl));
        }

        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(accessToken);
        resp.setRefreshToken(refreshToken);
        resp.setExpiresIn(accessTokenTtl);
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRoles(roles);
        return resp;
    }
}
