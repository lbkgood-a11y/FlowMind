package com.triobase.service.auth.controller;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.internal.RoleParticipantsResponse;
import com.triobase.common.dto.internal.UserValidationResponse;
import com.triobase.service.auth.service.InternalParticipantQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/process-participants")
@RequiredArgsConstructor
public class InternalParticipantController {

    private final InternalParticipantQueryService participantQueryService;

    @GetMapping("/roles/{roleCode}")
    public R<RoleParticipantsResponse> resolveRole(@PathVariable String roleCode) {
        return R.ok(participantQueryService.resolveRole(roleCode));
    }

    @GetMapping("/users/{userId}")
    public R<UserValidationResponse> validateUser(@PathVariable String userId) {
        return R.ok(participantQueryService.validateUser(userId));
    }
}
