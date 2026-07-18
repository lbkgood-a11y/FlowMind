package com.triobase.service.openapi.dto;

import com.triobase.service.openapi.domain.enums.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateApplicationRequest(@NotBlank String displayName, @NotBlank String ownerId,
                                       @NotBlank String purpose, @NotNull RiskLevel riskLevel) {
}
