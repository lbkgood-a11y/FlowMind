package com.triobase.service.workflow.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.workflow.dto.BusinessObjectCatalogResponse;
import com.triobase.service.workflow.dto.BusinessObjectSummaryResponse;
import com.triobase.service.workflow.service.BusinessObjectCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/process-business-objects")
@RequiredArgsConstructor
public class BusinessObjectCatalogController {

    private final BusinessObjectCatalogService catalogService;

    @GetMapping
    @RequirePermission("/api/v1/process-packages:GET")
    public R<List<BusinessObjectSummaryResponse>> list() {
        return R.ok(catalogService.listPublishedForCurrentTenant());
    }

    @GetMapping("/{typeCode}")
    @RequirePermission("/api/v1/process-packages:GET")
    public R<BusinessObjectCatalogResponse> detail(@PathVariable String typeCode) {
        return R.ok(catalogService.getPublishedDetail(typeCode));
    }

    @GetMapping("/{typeCode}/statuses")
    @RequirePermission("/api/v1/process-packages:GET")
    public R<List<BusinessObjectCatalogResponse.StatusItem>> statuses(@PathVariable String typeCode) {
        return R.ok(catalogService.getPublishedDetail(typeCode).getStatuses());
    }

    @GetMapping("/{typeCode}/forms")
    @RequirePermission("/api/v1/process-packages:GET")
    public R<List<BusinessObjectCatalogResponse.FormItem>> forms(@PathVariable String typeCode) {
        return R.ok(catalogService.getPublishedDetail(typeCode).getForms());
    }

    @GetMapping("/{typeCode}/permissions")
    @RequirePermission("/api/v1/process-packages:GET")
    public R<List<BusinessObjectCatalogResponse.PermissionItem>> permissions(@PathVariable String typeCode) {
        return R.ok(catalogService.getPublishedDetail(typeCode).getPermissions());
    }

    @GetMapping("/{typeCode}/actions")
    @RequirePermission("/api/v1/process-packages:GET")
    public R<List<BusinessObjectCatalogResponse.ActionItem>> actions(@PathVariable String typeCode) {
        return R.ok(catalogService.getPublishedDetail(typeCode).getActions());
    }

    @GetMapping("/{typeCode}/events")
    @RequirePermission("/api/v1/process-packages:GET")
    public R<List<BusinessObjectCatalogResponse.EventItem>> events(@PathVariable String typeCode) {
        return R.ok(catalogService.getPublishedDetail(typeCode).getEvents());
    }

    @GetMapping("/{typeCode}/agent-actions")
    @RequirePermission("/api/v1/process-packages:GET")
    public R<List<BusinessObjectCatalogResponse.AgentActionItem>> agentActions(@PathVariable String typeCode) {
        return R.ok(catalogService.getPublishedDetail(typeCode).getAgentActions());
    }
}
