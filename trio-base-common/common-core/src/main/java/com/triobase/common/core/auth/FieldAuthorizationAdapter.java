package com.triobase.common.core.auth;

import java.util.List;
import java.util.Map;

/**
 * Owner-service contract for applying centrally calculated field rules at API boundaries.
 * Implementations must invoke both read filtering and write validation server-side.
 */
public interface FieldAuthorizationAdapter<T> {

    FieldEnforcementManifest manifest();

    Map<String, Object> filterRead(T source, List<? extends FieldRule> rules);

    void validateWrite(Map<String, Object> changes, List<? extends FieldRule> rules);
}
