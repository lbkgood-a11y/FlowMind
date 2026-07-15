package com.triobase.service.lowcode.controller;

import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.lowcode.dto.CreateFormDefinitionRequest;
import com.triobase.service.lowcode.dto.FormDataResourceResponse;
import com.triobase.service.lowcode.dto.FormDefinitionResponse;
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
    public R<FormDefinitionResponse> create(@RequestBody CreateFormDefinitionRequest request,
                                            @RequestHeader(value = "X-Username", required = false) String operator) {
        return R.ok(formDefinitionService.create(request, operator));
    }

    @GetMapping
    public R<PageResult<FormDefinitionResponse>> list(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return R.ok(formDefinitionService.list(page, size));
    }

    @GetMapping("/data-resources")
    public R<List<FormDataResourceResponse>> listDataResources() {
        return R.ok(formDefinitionService.listPublishedDataResources());
    }

    @GetMapping("/{id}")
    public R<FormDefinitionResponse> getById(@PathVariable String id) {
        return R.ok(formDefinitionService.getById(id));
    }

    @PutMapping("/{id}/publish")
    public R<FormDefinitionResponse> publish(@PathVariable String id) {
        return R.ok(formDefinitionService.publish(id));
    }
}
