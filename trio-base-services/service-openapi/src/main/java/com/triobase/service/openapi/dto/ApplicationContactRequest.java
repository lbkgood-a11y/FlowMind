package com.triobase.service.openapi.dto;
import jakarta.validation.constraints.NotBlank;
public record ApplicationContactRequest(@NotBlank String role, @NotBlank String name, String email, String phone) { }
