package com.triobase.service.action.dto;

import com.triobase.common.action.definition.ActionDefinition;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ActionDefinitionSyncRequest {
    private String ownerService;
    private List<ActionDefinition> definitions = new ArrayList<>();
}
