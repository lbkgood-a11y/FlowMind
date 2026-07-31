package com.triobase.service.org.controller;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.internal.OrgOwnershipResponse;
import com.triobase.service.org.service.OrgUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/org-ownership")
@RequiredArgsConstructor
public class InternalOrgOwnershipController {

    private final OrgUnitService orgUnitService;

    @GetMapping("/users/{userId}/primary")
    public R<OrgOwnershipResponse> primaryOwnership(@PathVariable String userId,
                                                    @RequestParam String tenantId) {
        return R.ok(orgUnitService.resolvePrimaryOwnership(tenantId, userId));
    }
}
