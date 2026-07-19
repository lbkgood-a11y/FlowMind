package com.triobase.service.catalog.controller;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.catalog.BusinessObjectManifest;
import com.triobase.common.dto.catalog.BusinessObjectMetadata;
import com.triobase.service.catalog.service.BusinessCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/business-catalog")
@RequiredArgsConstructor
public class InternalBusinessCatalogController {

    private final BusinessCatalogService catalogService;

    @PostMapping("/manifests/sync")
    public R<BusinessObjectMetadata> syncManifest(@RequestBody BusinessObjectManifest manifest) {
        return R.ok(catalogService.sync(manifest));
    }
}
