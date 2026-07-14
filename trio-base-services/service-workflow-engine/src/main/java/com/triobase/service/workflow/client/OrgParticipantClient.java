package com.triobase.service.workflow.client;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.internal.OrgParticipantsResponse;
import com.triobase.service.workflow.config.InternalFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "workflow-org-internal",
        url = "${workflow.services.org-url}",
        configuration = InternalFeignConfiguration.class)
public interface OrgParticipantClient {

    @GetMapping("/internal/v1/process-participants/org-units/{orgUnitId}")
    R<OrgParticipantsResponse> resolveOrgUnit(
            @PathVariable String orgUnitId,
            @RequestParam(required = false) String dimensionCode);
}
