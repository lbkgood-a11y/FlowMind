package com.triobase.service.auth.dto;

import com.triobase.service.auth.entity.SysAuthPageCapability;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PageCapabilityResponse {
    private String id;
    private String pageCode;
    private String pageName;
    private String capabilityCode;
    private String capabilityName;
    private String category;
    private String helpText;
    private String readiness;
    private String readinessMessage;
    private Boolean scopeConfigurable;
    private Boolean fieldRestrictionConfigurable;
    private Integer sortOrder;
    private List<String> requiredCapabilityIds = new ArrayList<>();
    private List<FieldOption> availableFields = new ArrayList<>();
    private Boolean constraintConfigurable;

    @Data
    public static class FieldOption {
        private String fieldKey;
        private String fieldLabel;
    }

    public static PageCapabilityResponse from(SysAuthPageCapability capability) {
        PageCapabilityResponse response = new PageCapabilityResponse();
        response.setId(capability.getId());
        response.setPageCode(capability.getPageCode());
        response.setPageName(capability.getPageName());
        response.setCapabilityCode(capability.getCapabilityCode());
        response.setCapabilityName(capability.getCapabilityName());
        response.setCategory(capability.getCapabilityCategory());
        response.setHelpText(capability.getHelpText());
        response.setReadiness(capability.getReadinessStatus());
        response.setReadinessMessage(capability.getReadinessMessage());
        response.setScopeConfigurable(enabled(capability.getScopeSupported()));
        response.setFieldRestrictionConfigurable(enabled(capability.getFieldPolicySupported()));
        response.setSortOrder(capability.getSortOrder());
        return response;
    }

    private static boolean enabled(Short value) {
        return value != null && value == 1;
    }
}
