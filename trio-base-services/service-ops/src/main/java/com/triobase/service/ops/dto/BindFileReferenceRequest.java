package com.triobase.service.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindFileReferenceRequest {
    @NotBlank
    private String fileId;
    @NotBlank
    private String businessType;
    @NotBlank
    private String businessId;
    private String refType;
}
