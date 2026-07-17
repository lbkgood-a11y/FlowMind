package com.triobase.service.openapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCallbackProfileRequest(
        String tenantId,
        @NotBlank String displayName,
        @NotBlank String ownerId,
        @Valid @NotNull CallbackProfileVersionMutationRequest version) {
}
