package com.triobase.service.lowcode.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.service.FormInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FormInstanceController {

    private final FormInstanceService formInstanceService;

    @GetMapping("/api/v1/forms/{formKey}/instances")
    @RequirePermission("/api/v1/forms/*/instances:GET")
    public R<PageResult<FormInstanceResponse>> list(@PathVariable String formKey,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return R.ok(formInstanceService.list(formKey, page, size));
    }

    @GetMapping("/api/v1/forms/{formKey}/instances/{instanceId}/export")
    @RequirePermission("/api/v1/forms/*/instances/*/export:GET")
    public R<FormInstanceResponse> export(@PathVariable String formKey,
                                          @PathVariable String instanceId) {
        return R.ok(formInstanceService.export(formKey, instanceId));
    }

    @GetMapping("/api/v1/form-instances/{id}")
    @RequirePermission("/api/v1/form-instances/*:GET")
    public R<FormInstanceResponse> getById(@PathVariable String id) {
        return R.ok(formInstanceService.getById(id));
    }
}
