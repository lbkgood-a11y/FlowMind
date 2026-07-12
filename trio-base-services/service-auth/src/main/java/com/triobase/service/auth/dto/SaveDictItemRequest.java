package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class SaveDictItemRequest {
    private String dictTypeId;
    private String dictCode;
    private String itemLabel;
    private String itemValue;
    private String tagType;
    private String cssClass;
    private Integer status;
    private Integer systemFlag;
    private Integer sortOrder;
    private String description;
    private String metadata;
}
