package com.triobase.service.lowcode.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.lowcode.dto.CreateFormDefinitionRequest;
import com.triobase.service.lowcode.dto.FormDataResourceResponse;
import com.triobase.service.lowcode.dto.FormDefinitionResponse;
import com.triobase.service.lowcode.dto.UpdateFormDefinitionRequest;
import com.triobase.service.lowcode.service.FormDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/forms")
@RequiredArgsConstructor
public class FormDefinitionController {

    private final FormDefinitionService formDefinitionService;

    @PostMapping
    @RequirePermission("/api/v1/forms:POST")
    public R<FormDefinitionResponse> create(@RequestBody CreateFormDefinitionRequest request,
                                            @RequestHeader(value = "X-Username", required = false) String operator) {
        return R.ok(formDefinitionService.create(request, operator));
    }

    @GetMapping
    @RequirePermission("/api/v1/forms:GET")
    public R<PageResult<FormDefinitionResponse>> list(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return R.ok(formDefinitionService.list(page, size));
    }

    @GetMapping("/data-resources")
    @RequirePermission("/api/v1/forms:GET")
    public R<List<FormDataResourceResponse>> listDataResources() {
        return R.ok(formDefinitionService.listPublishedDataResources());
    }

    @GetMapping("/{id}")
    @RequirePermission("/api/v1/forms:GET")
    public R<FormDefinitionResponse> getById(@PathVariable String id) {
        return R.ok(formDefinitionService.getById(id));
    }

    @GetMapping("/{formKey}/versions")
    @RequirePermission("/api/v1/forms/*/versions:GET")
    public R<List<FormDefinitionResponse>> listVersions(@PathVariable String formKey) {
        return R.ok(formDefinitionService.listVersions(formKey));
    }

    @PutMapping("/{id}")
    @RequirePermission("/api/v1/forms/*:PUT")
    public R<FormDefinitionResponse> update(@PathVariable String id,
                                            @RequestBody UpdateFormDefinitionRequest request,
                                            @RequestHeader(value = "X-Username", required = false) String operator) {
        return R.ok(formDefinitionService.update(id, request, operator));
    }

    @PostMapping("/{id}/versions")
    @RequirePermission("/api/v1/forms/*/versions:POST")
    public R<FormDefinitionResponse> deriveNewVersion(@PathVariable String id,
                                                      @RequestHeader(value = "X-Username", required = false) String operator) {
        return R.ok(formDefinitionService.deriveNewVersion(id, operator));
    }

    @PutMapping("/{id}/publish")
    @RequirePermission("/api/v1/forms/*/publish:PUT")
    public R<FormDefinitionResponse> publish(@PathVariable String id) {
        return R.ok(formDefinitionService.publish(id));
    }

    @PutMapping("/{id}/offline")
    @RequirePermission("/api/v1/forms/*/offline:PUT")
    public R<FormDefinitionResponse> offline(@PathVariable String id) {
        return R.ok(formDefinitionService.offline(id));
    }
}
