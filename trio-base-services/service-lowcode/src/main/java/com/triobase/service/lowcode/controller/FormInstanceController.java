package com.triobase.service.lowcode.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.dto.BindFormProcessRequest;
import com.triobase.service.lowcode.dto.SubmitFormInstanceRequest;
import com.triobase.service.lowcode.dto.UpdateWorkflowStatusRequest;
import com.triobase.service.lowcode.service.FormInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FormInstanceController {

    private final FormInstanceService formInstanceService;

    @PostMapping("/api/v1/forms/{formKey}/submit")
    @RequirePermission("/api/v1/forms/*/submit:POST")
    public R<FormInstanceResponse> submit(@PathVariable String formKey,
                                          @RequestBody SubmitFormInstanceRequest request) {
        return R.ok(formInstanceService.submit(formKey, request));
    }

    @GetMapping("/api/v1/forms/{formKey}/instances")
    @RequirePermission("/api/v1/forms/*/instances:GET")
    public R<PageResult<FormInstanceResponse>> list(@PathVariable String formKey,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return R.ok(formInstanceService.list(formKey, page, size));
    }

    @PutMapping("/api/v1/forms/{formKey}/instances/{instanceId}/process")
    @RequirePermission("/api/v1/forms/*/instances/*/process:PUT")
    public R<FormInstanceResponse> bindProcess(@PathVariable String formKey,
                                               @PathVariable String instanceId,
                                               @RequestBody BindFormProcessRequest request) {
        return R.ok(formInstanceService.bindProcess(formKey, instanceId, request));
    }

    @PutMapping("/api/v1/forms/{formKey}/instances/{instanceId}/workflow-status")
    @RequirePermission("/api/v1/forms/*/instances/*/workflow-status:PUT")
    public R<FormInstanceResponse> updateWorkflowStatus(@PathVariable String formKey,
                                                        @PathVariable String instanceId,
                                                        @RequestBody UpdateWorkflowStatusRequest request) {
        return R.ok(formInstanceService.updateWorkflowStatus(formKey, instanceId, request));
    }

    @GetMapping("/api/v1/form-instances/{id}")
    @RequirePermission("/api/v1/form-instances/*:GET")
    public R<FormInstanceResponse> getById(@PathVariable String id) {
        return R.ok(formInstanceService.getById(id));
    }
}
