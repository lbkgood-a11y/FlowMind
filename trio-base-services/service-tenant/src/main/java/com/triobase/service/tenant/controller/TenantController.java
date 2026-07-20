package com.triobase.service.tenant.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.tenant.dto.CreateTenantRequest;
import com.triobase.service.tenant.dto.SaveTenantSettingRequest;
import com.triobase.service.tenant.dto.TenantResponse;
import com.triobase.service.tenant.dto.TenantSettingResponse;
import com.triobase.service.tenant.dto.UpdateTenantRequest;
import com.triobase.service.tenant.dto.UpdateTenantStatusRequest;
import com.triobase.service.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @RequirePermission("/api/v1/tenants:GET")
    public R<PageResult<TenantResponse>> pageTenants(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return R.ok(tenantService.pageTenants(keyword, status, page, size));
    }

    @GetMapping("/current")
    public R<TenantResponse> currentTenant() {
        return R.ok(tenantService.currentTenant());
    }

    @GetMapping("/{tenantId}")
    @RequirePermission("/api/v1/tenants/*:GET")
    public R<TenantResponse> getTenant(@PathVariable String tenantId) {
        return R.ok(tenantService.getTenant(tenantId));
    }

    @PostMapping
    @RequirePermission("/api/v1/tenants:POST")
    public R<TenantResponse> createTenant(@RequestBody CreateTenantRequest request) {
        return R.ok(tenantService.createTenant(request));
    }

    @PutMapping("/{tenantId}")
    @RequirePermission("/api/v1/tenants/*:PUT")
    public R<TenantResponse> updateTenant(@PathVariable String tenantId, @RequestBody UpdateTenantRequest request) {
        return R.ok(tenantService.updateTenant(tenantId, request));
    }

    @PutMapping("/{tenantId}/status")
    @RequirePermission("/api/v1/tenants/*:PUT")
    public R<TenantResponse> updateStatus(@PathVariable String tenantId,
                                          @RequestBody UpdateTenantStatusRequest request) {
        return R.ok(tenantService.updateStatus(tenantId, request));
    }

    @GetMapping("/{tenantId}/settings")
    @RequirePermission("/api/v1/tenants/*:GET")
    public R<List<TenantSettingResponse>> listSettings(@PathVariable String tenantId) {
        return R.ok(tenantService.listSettings(tenantId));
    }

    @PutMapping("/{tenantId}/settings/{settingKey}")
    @RequirePermission("/api/v1/tenants/*:PUT")
    public R<TenantSettingResponse> saveSetting(@PathVariable String tenantId,
                                                @PathVariable String settingKey,
                                                @RequestBody SaveTenantSettingRequest request) {
        return R.ok(tenantService.saveSetting(tenantId, settingKey, request));
    }

    @DeleteMapping("/{tenantId}/settings/{settingKey}")
    @RequirePermission("/api/v1/tenants/*:DELETE")
    public R<String> deleteSetting(@PathVariable String tenantId, @PathVariable String settingKey) {
        tenantService.deleteSetting(tenantId, settingKey);
        return R.ok("租户设置已删除");
    }
}
