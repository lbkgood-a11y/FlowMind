package com.triobase.service.auth.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.auth.dto.SystemConfigResponse;
import com.triobase.service.auth.dto.UpdateSystemConfigRequest;
import com.triobase.service.auth.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system-configs")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    @RequirePermission("/api/v1/system-configs:GET")
    public R<List<SystemConfigResponse>> list(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String configGroup,
                                              @RequestParam(required = false) Integer status) {
        return R.ok(systemConfigService.list(keyword, configGroup, status));
    }

    @GetMapping("/{id}")
    @RequirePermission("/api/v1/system-configs:GET")
    public R<SystemConfigResponse> detail(@PathVariable String id) {
        return R.ok(systemConfigService.detail(id));
    }

    @GetMapping("/runtime/{configKey}")
    @RequirePermission("/api/v1/system-configs:GET")
    public R<String> runtimeValue(@PathVariable String configKey) {
        return R.ok(systemConfigService.runtimeValue(configKey));
    }

    @PutMapping("/{id}")
    @RequirePermission("/api/v1/system-configs/*:PUT")
    public R<SystemConfigResponse> update(@PathVariable String id,
                                          @RequestBody UpdateSystemConfigRequest request) {
        return R.ok(systemConfigService.update(id, request));
    }
}
