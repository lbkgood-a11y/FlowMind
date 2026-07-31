package com.triobase.service.lowcode.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.lowcode.dto.OwnershipReconciliationResponse;
import com.triobase.service.lowcode.service.FormOwnershipReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/form-ownership")
@RequiredArgsConstructor
public class InternalOwnershipReconciliationController {

    private final FormOwnershipReconciliationService reconciliationService;

    @GetMapping("/unresolved-count")
    public R<Long> unresolvedCount(@RequestParam String tenantId) {
        return R.ok(reconciliationService.countUnresolved(tenantId));
    }

    @PostMapping("/reconcile")
    public R<OwnershipReconciliationResponse> reconcile(@RequestParam String tenantId,
                                                        @RequestParam(defaultValue = "200") int limit) {
        return R.ok(reconciliationService.reconcile(tenantId, limit));
    }
}
