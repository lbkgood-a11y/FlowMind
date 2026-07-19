package com.triobase.common.action.model;

import com.triobase.common.action.enums.ActionActorType;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ActionActor {
    private ActionActorType type;
    private String id;
    private String displayName;
    private String tenantId;
    private String delegatedBy;
    private String reason;
    private Map<String, Object> attributes = new LinkedHashMap<>();
}
