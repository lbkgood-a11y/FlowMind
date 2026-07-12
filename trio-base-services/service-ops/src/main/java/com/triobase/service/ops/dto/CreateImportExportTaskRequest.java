package com.triobase.service.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateImportExportTaskRequest {
    @NotBlank
    private String businessType;
    @NotBlank
    private String taskName;
    private String requestParams;
}
