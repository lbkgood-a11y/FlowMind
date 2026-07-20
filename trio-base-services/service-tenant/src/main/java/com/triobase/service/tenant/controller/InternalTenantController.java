package com.triobase.service.tenant.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.tenant.dto.TenantValidationResponse;
import com.triobase.service.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/tenants")
@RequiredArgsConstructor
public class InternalTenantController {

    private final TenantService tenantService;

    @GetMapping("/{tenantId}/validation")
    public R<TenantValidationResponse> validateTenant(@PathVariable String tenantId) {
        return R.ok(tenantService.validateTenant(tenantId));
    }
}
