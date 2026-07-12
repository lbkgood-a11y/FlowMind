package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class SaveDictTypeRequest {
    private String dictCode;
    private String dictName;
    private Integer status;
    private Integer systemFlag;
    private Integer sortOrder;
    private String description;
}
