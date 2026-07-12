package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class UpdateSystemConfigRequest {
    private String configValue;
    private String defaultValue;
    private String configType;
    private String configGroup;
    private Integer sensitive;
    private Integer status;
    private Integer sortOrder;
    private String description;
}
