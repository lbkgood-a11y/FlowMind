package com.triobase.service.workflow.exception;

import com.triobase.service.workflow.dto.ProcessVersionConflictResponse;
import lombok.Getter;

@Getter
public class ProcessVersionConflictException extends RuntimeException {

    private final ProcessVersionConflictResponse conflict;

    public ProcessVersionConflictException(ProcessVersionConflictResponse conflict) {
        super("PROCESS_VERSION_CONFLICT");
        this.conflict = conflict;
    }
}
