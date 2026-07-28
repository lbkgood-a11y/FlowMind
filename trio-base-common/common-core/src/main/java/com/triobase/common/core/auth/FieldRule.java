package com.triobase.common.core.auth;

/**
 * Minimal field-rule projection used by {@link FieldMaskHelper}.
 * Implementations live in downstream modules (e.g. {@code AuthzFieldRule} in common-dto).
 */
public interface FieldRule {

    String getFieldKey();

    String getReadMode();

    String getWriteMode();

    String getMaskStrategy();
}
