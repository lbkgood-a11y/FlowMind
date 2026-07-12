package com.triobase.service.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveJobRequest {
    @NotBlank
    private String jobCode;
    @NotBlank
    private String jobName;
    @NotBlank
    private String handlerName;
    @NotBlank
    private String cronExpression;
    private String jobParams;
    private Short enabled;
    private String description;
}
