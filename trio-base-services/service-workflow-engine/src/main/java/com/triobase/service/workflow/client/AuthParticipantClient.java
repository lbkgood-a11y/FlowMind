package com.triobase.service.workflow.client;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.internal.RoleParticipantsResponse;
import com.triobase.common.dto.internal.UserValidationResponse;
import com.triobase.service.workflow.config.InternalFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "workflow-auth-internal",
        url = "${workflow.services.auth-url}",
        configuration = InternalFeignConfiguration.class)
public interface AuthParticipantClient {

    @GetMapping("/internal/v1/process-participants/roles/{roleCode}")
    R<RoleParticipantsResponse> resolveRole(@PathVariable String roleCode);

    @GetMapping("/internal/v1/process-participants/users/{userId}")
    R<UserValidationResponse> validateUser(@PathVariable String userId);
}
