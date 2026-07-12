package com.triobase.service.auth.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.R;
import com.triobase.service.auth.dto.DataPolicyResponse;
import com.triobase.service.auth.dto.EffectiveDataPolicyResponse;
import com.triobase.service.auth.dto.SaveDataPolicyRequest;
import com.triobase.service.auth.service.DataPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/data-policies")
@RequiredArgsConstructor
public class DataPolicyController {

    private final DataPolicyService dataPolicyService;

    @GetMapping
    @RequirePermission("/api/v1/data-policies:GET")
    public R<List<DataPolicyResponse>> list(@RequestParam(required = false) String roleId) {
        return R.ok(dataPolicyService.listByRole(roleId));
    }

    @GetMapping("/{id}")
    @RequirePermission("/api/v1/data-policies:GET")
    public R<DataPolicyResponse> detail(@PathVariable String id) {
        return R.ok(dataPolicyService.findById(id));
    }

    @GetMapping("/effective")
    @RequirePermission("/api/v1/data-policies/effective:GET")
    public R<EffectiveDataPolicyResponse> effective(@RequestParam(required = false) String userId,
                                                    @RequestParam String resourceCode,
                                                    @RequestParam String actionCode) {
        String currentUserId = SecurityContextHolder.getUserId();
        if (!StringUtils.hasText(currentUserId)) {
            throw new BizException(AuthErrorCode.PERMISSION_DENIED);
        }
        String targetUserId = StringUtils.hasText(userId) ? userId : currentUserId;
        boolean queryingSelf = currentUserId.equals(targetUserId);
        boolean canManageDataPolicies = SecurityContextHolder.getPermissions()
                .contains("/api/v1/data-policies:GET");
        if (!queryingSelf && !canManageDataPolicies) {
            throw new BizException(AuthErrorCode.PERMISSION_DENIED);
        }
        return R.ok(dataPolicyService.resolveEffective(targetUserId, resourceCode, actionCode));
    }

    @PostMapping
    @RequirePermission("/api/v1/data-policies:POST")
    public R<DataPolicyResponse> create(@RequestBody SaveDataPolicyRequest request) {
        return R.ok(dataPolicyService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("/api/v1/data-policies/*:PUT")
    public R<DataPolicyResponse> update(@PathVariable String id,
                                        @RequestBody SaveDataPolicyRequest request) {
        return R.ok(dataPolicyService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("/api/v1/data-policies/*:DELETE")
    public R<String> delete(@PathVariable String id) {
        dataPolicyService.delete(id);
        return R.ok("数据权限策略已删除");
    }
}
