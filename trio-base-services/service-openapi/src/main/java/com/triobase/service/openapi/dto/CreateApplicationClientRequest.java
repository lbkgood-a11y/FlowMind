package com.triobase.service.openapi.dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.service.openapi.domain.enums.Environment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
public record CreateApplicationClientRequest(@NotNull Environment environment, @NotBlank String clientKey,
        JsonNode networkPolicy, JsonNode securityPolicy, LocalDateTime expiresAt) { }
