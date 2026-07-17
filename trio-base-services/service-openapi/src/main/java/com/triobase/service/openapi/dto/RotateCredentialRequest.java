package com.triobase.service.openapi.dto;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
public record RotateCredentialRequest(@NotNull AuthenticationType authenticationType,@NotBlank String newSecretReference,
                                      long overlapSeconds, LocalDateTime expiresAt) { }
