package com.triobase.service.org.controller;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.internal.OrgParticipantsResponse;
import com.triobase.service.org.service.OrgUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/process-participants")
@RequiredArgsConstructor
public class InternalOrgParticipantController {

    private final OrgUnitService orgUnitService;

    @GetMapping("/org-units/{orgUnitId}")
    public R<OrgParticipantsResponse> resolveOrgUnit(
            @PathVariable String orgUnitId,
            @RequestParam(required = false) String dimensionCode) {
        return R.ok(orgUnitService.resolveEnabledUsers(orgUnitId, dimensionCode));
    }
}
