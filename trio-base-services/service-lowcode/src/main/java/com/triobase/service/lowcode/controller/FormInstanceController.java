package com.triobase.service.lowcode.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.lowcode.dto.FormInstanceResponse;
import com.triobase.service.lowcode.dto.SubmitFormInstanceRequest;
import com.triobase.service.lowcode.service.FormInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FormInstanceController {

    private final FormInstanceService formInstanceService;

    @PostMapping("/api/v1/forms/{formKey}/submit")
    public R<FormInstanceResponse> submit(@PathVariable String formKey,
                                          @RequestBody SubmitFormInstanceRequest request) {
        return R.ok(formInstanceService.submit(formKey, request));
    }

    @GetMapping("/api/v1/form-instances/{id}")
    public R<FormInstanceResponse> getById(@PathVariable String id) {
        return R.ok(formInstanceService.getById(id));
    }
}
