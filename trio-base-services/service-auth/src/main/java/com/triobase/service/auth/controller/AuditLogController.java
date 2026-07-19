package com.triobase.service.auth.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.auth.entity.SysOperationAuditLog;
import com.triobase.service.auth.service.OperationAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final OperationAuditService operationAuditService;

    @GetMapping
    @RequirePermission("/api/v1/audit-logs:GET")
    public R<PageResult<SysOperationAuditLog>> page(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size,
                                                    @RequestParam(required = false) String username,
                                                    @RequestParam(required = false) String userId,
                                                    @RequestParam(required = false) String requestPath,
                                                    @RequestParam(required = false) String resultStatus,
                                                    @RequestParam(required = false) String actionId,
                                                    @RequestParam(required = false) String actionType,
                                                    @RequestParam(required = false) String actionSource,
                                                    @RequestParam(required = false) String actionStatus,
                                                    @RequestParam(required = false) String actionTargetType,
                                                    @RequestParam(required = false) String actionTargetId,
                                                    @RequestParam(required = false) String actionCorrelationId,
                                                    @RequestParam(required = false) String actionIdempotencyKey,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                    LocalDateTime operatedStart,
                                                    @RequestParam(required = false)
                                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                    LocalDateTime operatedEnd) {
        return R.ok(operationAuditService.page(page, size, username, userId, requestPath,
                resultStatus, actionId, actionType, actionSource, actionStatus, actionTargetType, actionTargetId,
                actionCorrelationId, actionIdempotencyKey, operatedStart, operatedEnd));
    }

    @GetMapping("/{id}")
    @RequirePermission("/api/v1/audit-logs:GET")
    public R<SysOperationAuditLog> detail(@PathVariable String id) {
        return R.ok(operationAuditService.detail(id));
    }
}
