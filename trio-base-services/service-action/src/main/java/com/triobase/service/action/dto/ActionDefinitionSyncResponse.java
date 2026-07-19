package com.triobase.service.action.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class ActionDefinitionSyncResponse {
    private String ownerService;
    private int synchronizedCount;
    private Map<String, Integer> versions = new LinkedHashMap<>();
    private List<ActionDefinitionDiagnostic> diagnostics = new ArrayList<>();
}
