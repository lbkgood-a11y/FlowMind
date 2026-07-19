package com.triobase.service.catalog.controller;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.catalog.BusinessObjectMetadata;
import com.triobase.service.catalog.service.BusinessCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business-catalog")
@RequiredArgsConstructor
public class BusinessCatalogController {

    private final BusinessCatalogService catalogService;

    @GetMapping("/objects")
    public R<List<BusinessObjectMetadata>> objects() {
        return R.ok(catalogService.listEffectiveForCurrentTenant());
    }

    @GetMapping("/objects/{objectType}")
    public R<BusinessObjectMetadata> object(@PathVariable String objectType) {
        return R.ok(catalogService.getEffectiveForCurrentTenant(objectType));
    }
}
