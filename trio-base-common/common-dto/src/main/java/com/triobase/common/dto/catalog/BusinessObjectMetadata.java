package com.triobase.common.dto.catalog;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class BusinessObjectMetadata {
    private String tenantId;
    private String objectType;
    private String displayName;
    private String ownerService;
    private String description;
    private Integer version;
    private String lifecycleStatus;
    private List<BusinessStatusMetadata> statuses = new ArrayList<>();
    private List<BusinessActionMetadata> actions = new ArrayList<>();
    private List<BusinessFieldMetadata> fields = new ArrayList<>();
    private BusinessPageMetadata page = new BusinessPageMetadata();
    private Map<String, Object> attributes = new LinkedHashMap<>();
}
