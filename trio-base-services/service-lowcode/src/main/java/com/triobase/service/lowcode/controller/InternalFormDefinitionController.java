package com.triobase.service.lowcode.controller;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.internal.PublishedFormSnapshotResponse;
import com.triobase.service.lowcode.service.FormDefinitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/process-forms")
@RequiredArgsConstructor
public class InternalFormDefinitionController {

    private final FormDefinitionService formDefinitionService;

    @GetMapping("/{id}")
    public R<PublishedFormSnapshotResponse> getPublishedForm(@PathVariable String id) {
        return R.ok(formDefinitionService.getPublishedSnapshot(id));
    }
}
