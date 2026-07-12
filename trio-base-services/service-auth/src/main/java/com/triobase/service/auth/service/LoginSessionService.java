package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.jwt.JwtUtil;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.auth.LoginResponse;
import com.triobase.service.auth.entity.SysLoginLog;
import com.triobase.service.auth.entity.SysUser;
import com.triobase.service.auth.entity.SysUserSession;
import com.triobase.service.auth.mapper.LoginLogMapper;
import com.triobase.service.auth.mapper.UserSessionMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class LoginSessionService {

    private static final String DEFAULT_TENANT = "default";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final LoginLogMapper loginLogMapper;
    private final UserSessionMapper userSessionMapper;

    public void recordLoginSuccess(SysUser user) {
        SysLoginLog log = baseLoginLog(user != null ? user.getUsername() : null);
        if (user != null) {
            log.setUserId(user.getId());
            log.setUsername(user.getUsername());
        }
        log.setLoginResult("SUCCESS");
        loginLogMapper.insert(log);
    }

    public void recordLoginFailure(String username, String reason) {
        SysLoginLog log = baseLoginLog(username);
        log.setLoginResult("FAILURE");
        log.setFailureReason(limit(reason, 256));
        loginLogMapper.insert(log);
    }

    public void recordSession(SysUser user, LoginResponse response, String jwtSecret) {
        if (user == null || response == null || !StringUtils.hasText(response.getAccessToken())) {
            return;
        }
        JwtUtil.JwtPayload access = JwtUtil.verifyAndParse(response.getAccessToken(), jwtSecret);
        JwtUtil.JwtPayload refresh = StringUtils.hasText(response.getRefreshToken())
                ? JwtUtil.verifyAndParse(response.getRefreshToken(), jwtSecret)
                : null;
        if (!access.valid()) {
            return;
        }
        HttpServletRequest request = currentRequest();
        SysUserSession session = new SysUserSession();
        session.setId(UlidGenerator.nextUlid());
        session.setTenantId(DEFAULT_TENANT);
        session.setUserId(user.getId());
        session.setUsername(user.getUsername());
        session.setAccessJti(access.jti());
        session.setRefreshJti(refresh != null && refresh.valid() ? refresh.jti() : null);
        session.setSessionStatus(STATUS_ACTIVE);
        session.setClientIp(clientIp(request));
        session.setUserAgent(limit(request != null ? request.getHeader("User-Agent") : null, 512));
        session.setIssuedAt(LocalDateTime.now());
        session.setExpiresAt(toLocalDateTime(access));
        session.setRefreshExpiresAt(refresh != null && refresh.valid() ? toLocalDateTime(refresh) : null);
        session.setLastActiveAt(LocalDateTime.now());
        session.setTraceId(traceId(request));
        userSessionMapper.insert(session);
    }

    public void markLogout(String accessToken, String jwtSecret) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        JwtUtil.JwtPayload access = JwtUtil.verifyAndParse(accessToken, jwtSecret);
        if (!access.valid() || !StringUtils.hasText(access.jti())) {
            return;
        }
        SysUserSession session = findByAccessJti(access.jti());
        if (session == null) {
            return;
        }
        session.setSessionStatus("LOGGED_OUT");
        session.setLogoutAt(LocalDateTime.now());
        session.setLastActiveAt(LocalDateTime.now());
        userSessionMapper.updateById(session);
    }

    public PageResult<SysLoginLog> pageLoginLogs(int page,
                                                 int size,
                                                 String username,
                                                 String loginResult,
                                                 LocalDateTime loginStart,
                                                 LocalDateTime loginEnd) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<SysLoginLog>()
                .eq(SysLoginLog::getTenantId, DEFAULT_TENANT)
                .like(StringUtils.hasText(username), SysLoginLog::getUsername, username)
                .eq(StringUtils.hasText(loginResult), SysLoginLog::getLoginResult, loginResult)
                .ge(loginStart != null, SysLoginLog::getLoginAt, loginStart)
                .le(loginEnd != null, SysLoginLog::getLoginAt, loginEnd)
                .orderByDesc(SysLoginLog::getLoginAt);
        IPage<SysLoginLog> result = loginLogMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    public PageResult<SysUserSession> pageSessions(int page,
                                                   int size,
                                                   String username,
                                                   String sessionStatus,
                                                   LocalDateTime activeStart,
                                                   LocalDateTime activeEnd) {
        LambdaQueryWrapper<SysUserSession> wrapper = new LambdaQueryWrapper<SysUserSession>()
                .eq(SysUserSession::getTenantId, DEFAULT_TENANT)
                .like(StringUtils.hasText(username), SysUserSession::getUsername, username)
                .eq(StringUtils.hasText(sessionStatus), SysUserSession::getSessionStatus, sessionStatus)
                .ge(activeStart != null, SysUserSession::getLastActiveAt, activeStart)
                .le(activeEnd != null, SysUserSession::getLastActiveAt, activeEnd)
                .orderByDesc(SysUserSession::getLastActiveAt);
        IPage<SysUserSession> result = userSessionMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    public SysUserSession revoke(String id) {
        SysUserSession session = userSessionMapper.selectById(id);
        if (session == null) {
            throw new BizException(40472, "SESSION_NOT_FOUND");
        }
        session.setSessionStatus("REVOKED");
        session.setRevokedBy(SecurityContextHolder.getUserId());
        session.setRevokedAt(LocalDateTime.now());
        session.setLastActiveAt(LocalDateTime.now());
        userSessionMapper.updateById(session);
        return session;
    }

    public boolean isAccessJtiInactive(String accessJti) {
        if (!StringUtils.hasText(accessJti)) {
            return false;
        }
        SysUserSession session = findByAccessJti(accessJti);
        return session != null && !"ACTIVE".equals(session.getSessionStatus());
    }

    private SysLoginLog baseLoginLog(String username) {
        HttpServletRequest request = currentRequest();
        SysLoginLog log = new SysLoginLog();
        log.setId(UlidGenerator.nextUlid());
        log.setTenantId(DEFAULT_TENANT);
        log.setUsername(username);
        log.setClientIp(clientIp(request));
        log.setUserAgent(limit(request != null ? request.getHeader("User-Agent") : null, 512));
        log.setTraceId(traceId(request));
        log.setLoginAt(LocalDateTime.now());
        return log;
    }

    private SysUserSession findByAccessJti(String accessJti) {
        return userSessionMapper.selectOne(new LambdaQueryWrapper<SysUserSession>()
                .eq(SysUserSession::getAccessJti, accessJti)
                .last("LIMIT 1"));
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        return StringUtils.hasText(realIp) ? realIp : request.getRemoteAddr();
    }

    private String traceId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String traceId = request.getHeader("X-B3-TraceId");
        return StringUtils.hasText(traceId) ? traceId : request.getHeader("traceparent");
    }

    private LocalDateTime toLocalDateTime(JwtUtil.JwtPayload payload) {
        return payload.expirationTime() != null
                ? LocalDateTime.ofInstant(payload.expirationTime().toInstant(), ZoneId.systemDefault())
                : null;
    }

    private String limit(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
