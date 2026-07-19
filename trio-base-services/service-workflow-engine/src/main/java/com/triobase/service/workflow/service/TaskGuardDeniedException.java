package com.triobase.service.workflow.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.authz.AuthzGuardResult;
import lombok.Getter;

import java.util.List;

@Getter
public class TaskGuardDeniedException extends BizException {

    private final List<AuthzGuardResult> guardResults;

    public TaskGuardDeniedException(int code, String message, List<AuthzGuardResult> guardResults) {
        super(code, message);
        this.guardResults = guardResults == null ? List.of() : List.copyOf(guardResults);
    }
}
