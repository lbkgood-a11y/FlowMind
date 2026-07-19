package com.triobase.service.workflow.controller;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.catalog.BusinessObjectMetadata;
import com.triobase.service.workflow.service.WorkflowBusinessCatalogBridgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/workflow-business-catalog")
@RequiredArgsConstructor
public class InternalBusinessCatalogBridgeController {

    private final WorkflowBusinessCatalogBridgeService bridgeService;

    @GetMapping("/objects/{typeCode}")
    public R<BusinessObjectMetadata> object(@PathVariable String typeCode) {
        return R.ok(bridgeService.getSharedMetadata(typeCode));
    }
}
