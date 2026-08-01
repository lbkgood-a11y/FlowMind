package com.triobase.service.auth.controller;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.authz.AuthorizationBatchDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationBatchDecisionResponse;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.common.dto.authz.AuthorizationResourceSyncRequest;
import com.triobase.common.dto.authz.AuthzFieldRule;
import com.triobase.service.auth.dto.AuthorizationSyncResponse;
import com.triobase.service.auth.service.AuthorizationDecisionService;
import com.triobase.service.auth.service.AuthorizationRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/authz")
@RequiredArgsConstructor
public class InternalAuthorizationController {

    private final AuthorizationDecisionService decisionService;
    private final AuthorizationRegistryService registryService;

    @PostMapping("/resources/sync")
    public R<AuthorizationSyncResponse> syncResources(@RequestBody AuthorizationResourceSyncRequest request) {
        return R.ok(registryService.synchronize(request));
    }

    @PostMapping("/decide")
    public R<AuthorizationDecisionResponse> decide(@RequestBody AuthorizationDecisionRequest request) {
        return R.ok(decisionService.decide(request));
    }

    @PostMapping("/field-rules/effective")
    public R<List<AuthzFieldRule>> effectiveFieldRules(
            @RequestBody AuthorizationDecisionRequest request) {
        return R.ok(decisionService.effectiveFieldRules(request));
    }

    @PostMapping("/batch-decide")
    public R<AuthorizationBatchDecisionResponse> batchDecide(@RequestBody AuthorizationBatchDecisionRequest request) {
        return R.ok(decisionService.batchDecide(request));
    }
}
