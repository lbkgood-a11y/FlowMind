package com.triobase.service.auth.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class RoleAuthorizationValidationResponse {
    String validationToken;
    LocalDateTime expiresAt;
    String businessSummary;
    List<String> blockingErrors;
    List<String> warnings;
    RoleAuthorizationCompilationPlan compilation;
    Long affectedUserCount;
}
