package com.triobase.common.dto.authz;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Versioned contract used by product modules and low-code publication to declare
 * business-facing page capabilities. Runtime targets remain technical evidence
 * and must not be exposed by implementation-person APIs.
 */
@Data
public class PageCapabilityManifestSyncRequest {
    private String tenantId;
    private String catalogCode;
    private Long catalogVersion;
    private String sourceType;
    private String sourceRef;
    private List<Page> pages = new ArrayList<>();

    @Data
    public static class Page {
        private String pageCode;
        private String pageName;
        private String menuKey;
        private Integer sortOrder;
        private String metadataJson;
        private List<Capability> capabilities = new ArrayList<>();
    }

    @Data
    public static class Capability {
        private String capabilityCode;
        private String capabilityName;
        private String category;
        private String helpText;
        private Boolean scopeSupported;
        private FieldEnforcementRequirement fieldEnforcement;
        private Integer sortOrder;
        private String metadataJson;
        private List<String> requiredCapabilityCodes = new ArrayList<>();
        private List<Target> targets = new ArrayList<>();
        private List<String> guardCodes = new ArrayList<>();
    }

    @Data
    public static class Target {
        private String resourceCode;
        private String actionCode;
        private String targetKind = "GRANT";
        private Boolean required = true;
    }

    @Data
    public static class FieldEnforcementRequirement {
        private Boolean readHideRequired = false;
        private Boolean readMaskRequired = false;
        private Boolean writeDenyRequired = false;

        public boolean anyRequired() {
            return Boolean.TRUE.equals(readHideRequired)
                    || Boolean.TRUE.equals(readMaskRequired)
                    || Boolean.TRUE.equals(writeDenyRequired);
        }
    }
}
