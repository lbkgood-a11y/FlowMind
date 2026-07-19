package com.triobase.service.lowcode.service;

import com.triobase.common.core.exception.BizException;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

final class LowcodeAuthorizationActionCatalog {

    private static final Map<String, String> APPLICATION_ACTION_TYPES = new TreeMap<>(Map.of(
            "CREATE", "CREATE",
            "SAVE", "CREATE",
            "SUBMIT", "SUBMIT",
            "SUBMIT_AND_LAUNCH_WORKFLOW", "SUBMIT",
            "OPEN_DETAIL", "VIEW",
            "OPEN_PROCESS", "VIEW",
            "RETRY_WORKFLOW", "SUBMIT"
    ));

    private LowcodeAuthorizationActionCatalog() {
    }

    static Set<String> supportedApplicationActionTypes() {
        return APPLICATION_ACTION_TYPES.keySet();
    }

    static String formActionForApplicationActionType(String actionType) {
        String normalized = normalize(actionType);
        String mapped = APPLICATION_ACTION_TYPES.get(normalized);
        if (!StringUtils.hasText(mapped)) {
            throw new BizException(40050, "APPLICATION_ACTION_AUTHORIZATION_UNSUPPORTED");
        }
        return mapped;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
