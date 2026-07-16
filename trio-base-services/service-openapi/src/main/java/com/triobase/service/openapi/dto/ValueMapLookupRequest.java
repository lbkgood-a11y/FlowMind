package com.triobase.service.openapi.dto;

import jakarta.validation.constraints.NotBlank;

public record ValueMapLookupRequest(
        @NotBlank String value,
        boolean canonicalToExternal) {
}
