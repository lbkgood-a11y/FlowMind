package com.triobase.common.dto.catalog;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BusinessActionMetadata {
    private String actionCode;
    private String actionType;
    private String displayName;
    private String actionGroup;
    private Integer sortOrder;
    private boolean primary;
    private boolean danger;
    private boolean requiresConfirmation;
    private String confirmationTitle;
    private String confirmationMessage;
    private String executionMode;
    private String ownerService;
    private String permissionCode;
    private String payloadSchemaJson;
    private String resultSchemaJson;
    private String targetStatus;
    private String targetStatusGroup;
    private List<String> refreshScopes = new ArrayList<>();
}
