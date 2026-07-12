package com.triobase.service.auth.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.auth.entity.SysLoginLog;
import com.triobase.service.auth.entity.SysUserSession;
import com.triobase.service.auth.service.LoginSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final LoginSessionService loginSessionService;

    @GetMapping("/login-logs")
    @RequirePermission("/api/v1/sessions:GET")
    public R<PageResult<SysLoginLog>> loginLogs(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String username,
                                                @RequestParam(required = false) String loginResult,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                LocalDateTime loginStart,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                LocalDateTime loginEnd) {
        return R.ok(loginSessionService.pageLoginLogs(page, size, username, loginResult, loginStart, loginEnd));
    }

    @GetMapping
    @RequirePermission("/api/v1/sessions:GET")
    public R<PageResult<SysUserSession>> sessions(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String username,
                                                  @RequestParam(required = false) String sessionStatus,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                  LocalDateTime activeStart,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                  LocalDateTime activeEnd) {
        return R.ok(loginSessionService.pageSessions(page, size, username, sessionStatus, activeStart, activeEnd));
    }

    @PutMapping("/{id}/revoke")
    @RequirePermission("/api/v1/sessions/*:PUT")
    public R<SysUserSession> revoke(@PathVariable String id) {
        return R.ok(loginSessionService.revoke(id));
    }
}
