package com.triobase.common.dto.catalog;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class BusinessFieldMetadata {
    private String fieldKey;
    private String displayName;
    private String dataType;
    private String sectionKey;
    private boolean visible = true;
    private boolean editable = true;
    private boolean required;
    private boolean masked;
    private boolean gridEditable;
    private Integer sortOrder;
    private Map<String, Object> componentProps = new LinkedHashMap<>();
}
