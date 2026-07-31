package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ReplaceRoleCapabilityIntentRequest {
    private String tenantId;
    private Long expectedVersion;
    private List<Selection> selections = new ArrayList<>();
    private List<String> removedCapabilityIds = new ArrayList<>();

    @Data
    public static class Selection {
        private String capabilityId;
        private String selectionSource = "EXPLICIT";
        private String defaultScopeType;
        private List<String> defaultScopeIds = new ArrayList<>();
        private String operationScopeType;
        private List<String> operationScopeIds = new ArrayList<>();
        private String fieldIntentJson;
        private String constraintIntentJson;
    }
}
