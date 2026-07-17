package com.triobase.service.openapi.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.dto.CallbackProfileVersionMutationRequest;
import com.triobase.service.openapi.dto.CallbackProfileVersionResponse;
import com.triobase.service.openapi.dto.CreateCallbackProfileRequest;
import com.triobase.service.openapi.service.CallbackProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/openapi/management/callback-profiles")
@RequiredArgsConstructor
public class CallbackProfileManagementController {

    private static final String READ = "/api/v1/openapi/management/callback-profiles:GET";
    private static final String WRITE = "/api/v1/openapi/management/callback-profiles:POST";
    private final CallbackProfileService service;

    @PostMapping
    @RequirePermission(WRITE)
    public R<CallbackProfileVersionResponse> create(
            @Valid @RequestBody CreateCallbackProfileRequest request) {
        return R.ok(service.create(request));
    }

    @GetMapping("/versions/{versionId}")
    @RequirePermission(READ)
    public R<CallbackProfileVersionResponse> get(@PathVariable String versionId) {
        return R.ok(service.getVersion(versionId));
    }

    @PostMapping("/{profileId}/versions")
    @RequirePermission(WRITE)
    public R<CallbackProfileVersionResponse> createDraft(
            @PathVariable String profileId,
            @Valid @RequestBody CallbackProfileVersionMutationRequest request) {
        return R.ok(service.createDraft(profileId, request));
    }

    @PutMapping("/versions/{versionId}")
    @RequirePermission(WRITE)
    public R<CallbackProfileVersionResponse> updateDraft(
            @PathVariable String versionId,
            @Valid @RequestBody CallbackProfileVersionMutationRequest request) {
        return R.ok(service.updateDraft(versionId, request));
    }

    @PostMapping("/versions/{versionId}/publish")
    @RequirePermission(WRITE)
    public R<CallbackProfileVersionResponse> publish(@PathVariable String versionId) {
        return R.ok(service.publish(versionId));
    }

    @PostMapping("/versions/{versionId}/deprecate")
    @RequirePermission(WRITE)
    public R<CallbackProfileVersionResponse> deprecate(@PathVariable String versionId) {
        return R.ok(service.deprecate(versionId));
    }
}
