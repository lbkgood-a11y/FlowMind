package com.triobase.common.action.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ActionTypeValidator {

    private static final Pattern ACTION_TYPE = Pattern.compile(
            "^[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*){1,7}$");

    private ActionTypeValidator() {
    }

    public static boolean valid(String actionType) {
        return actionType != null && ACTION_TYPE.matcher(actionType.trim()).matches();
    }

    public static String requireValid(String actionType) {
        String normalized = normalize(actionType);
        if (!valid(normalized)) {
            throw new IllegalArgumentException("Invalid actionType: " + actionType);
        }
        return normalized;
    }

    public static String normalize(String actionType) {
        if (actionType == null) {
            return null;
        }
        String trimmed = actionType.trim();
        if (!trimmed.contains(".")) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
        return trimmed;
    }
}
