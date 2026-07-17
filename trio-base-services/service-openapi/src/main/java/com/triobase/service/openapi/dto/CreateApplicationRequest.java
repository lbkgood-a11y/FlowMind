package com.triobase.service.openapi.dto;
import com.triobase.service.openapi.domain.enums.RiskLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
public record CreateApplicationRequest(String tenantId, @NotBlank String applicationKey, @NotBlank String displayName,
        @NotBlank String ownerId, @NotBlank String purpose, @NotNull RiskLevel riskLevel,
        @Valid List<ApplicationContactRequest> contacts) { }
