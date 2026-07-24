package com.triobase.service.lowcode.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class RuntimeApplicationDescriptorResponse extends RuntimeApplicationSummaryResponse {
    private String primaryFormDefinitionId;
    private String schemaJson;
    private String uiSchemaJson;
    private List<ApplicationPageResponse> pages;
    private List<ApplicationActionResponse> actions;
    private List<RuntimeFieldAuthorizationResponse> fieldRules;
    private List<FormRelationResponse> relations;
}
