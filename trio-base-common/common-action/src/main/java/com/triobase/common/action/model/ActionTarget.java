package com.triobase.common.action.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ActionTarget {
    private String type;
    private String id;
    private String ownerService;
    private String tenantId;
    private String version;
    private Map<String, Object> attributes = new LinkedHashMap<>();
}
