package com.triobase.service.action.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionDefinitionDiagnostic {
    private String actionType;
    private String ownerService;
    private String targetType;
    private boolean registered;
    private boolean snapshotExists;
    private boolean duplicateVersions;
    private boolean ownerCompatible = true;
    private boolean compatible = true;
    private Integer latestVersion;
    private String latestSchemaHash;
    private List<Integer> versions = new ArrayList<>();
    private List<String> issues = new ArrayList<>();
}
