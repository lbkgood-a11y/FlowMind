package com.triobase.common.core.auth;

import java.util.Set;

/**
 * Owner declaration proving that a resource applies centrally calculated field
 * rules at its read and write boundaries.
 */
public record FieldEnforcementManifest(
        String ownerService,
        String resourceCode,
        String resourceType,
        Set<String> fieldKeys,
        boolean readHideEnforced,
        boolean readMaskEnforced,
        boolean writeDenyEnforced,
        Set<String> coveredBoundaries
) {
    public FieldEnforcementManifest {
        fieldKeys = fieldKeys != null ? Set.copyOf(fieldKeys) : Set.of();
        coveredBoundaries = coveredBoundaries != null ? Set.copyOf(coveredBoundaries) : Set.of();
    }

    public boolean supportsReadPolicy(String readMode) {
        if (readMode == null || readMode.isBlank() || "VISIBLE".equalsIgnoreCase(readMode)) {
            return true;
        }
        return "MASKED".equalsIgnoreCase(readMode) ? readMaskEnforced : readHideEnforced;
    }

    public boolean supportsWritePolicy(String writeMode) {
        return writeMode == null || writeMode.isBlank() || "EDITABLE".equalsIgnoreCase(writeMode)
                || writeDenyEnforced;
    }
}
