package com.triobase.service.lowcode.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.lowcode.dto.AuthorizationPublicationStatusResponse;
import com.triobase.service.lowcode.service.AuthorizationPublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lowcode-authorization-publications")
public class AuthorizationPublicationController {

    private final AuthorizationPublicationService publicationService;

    @GetMapping
    @RequirePermission("/api/v1/lowcode-applications:GET")
    public R<List<AuthorizationPublicationStatusResponse>> list() {
        return R.ok(publicationService.listCurrentTenant());
    }

    @PostMapping("/{eventId}/retry")
    @RequirePermission("/api/v1/lowcode-applications/*:PUT")
    public R<Void> retry(@PathVariable String eventId) {
        publicationService.retry(eventId);
        return R.ok();
    }

    @PostMapping("/{eventId}/reconcile")
    @RequirePermission("/api/v1/lowcode-applications/*:PUT")
    public R<AuthorizationPublicationStatusResponse> reconcile(@PathVariable String eventId) {
        return R.ok(publicationService.reconcile(eventId));
    }
}
