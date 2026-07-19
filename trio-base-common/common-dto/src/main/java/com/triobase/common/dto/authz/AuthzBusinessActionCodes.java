package com.triobase.common.dto.authz;

import java.util.Locale;
import java.util.Set;

public final class AuthzBusinessActionCodes {

    public static final String VIEW = "VIEW";
    public static final String CREATE = "CREATE";
    public static final String EDIT = "EDIT";
    public static final String DELETE = "DELETE";
    public static final String SUBMIT = "SUBMIT";
    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";
    public static final String CANCEL = "CANCEL";
    public static final String CLOSE = "CLOSE";
    public static final String CONFIRM = "CONFIRM";
    public static final String RETRY = "RETRY";
    public static final String EXPORT = "EXPORT";

    private static final Set<String> DOCUMENT_ACTIONS = Set.of(
            VIEW,
            CREATE,
            EDIT,
            DELETE,
            SUBMIT,
            APPROVE,
            REJECT,
            CANCEL,
            CLOSE,
            CONFIRM,
            RETRY,
            EXPORT);

    private AuthzBusinessActionCodes() {
    }

    public static String normalize(String actionCode) {
        return actionCode == null ? null : actionCode.trim().toUpperCase(Locale.ROOT);
    }

    public static boolean isDocumentAction(String actionCode) {
        String normalized = normalize(actionCode);
        return normalized != null && DOCUMENT_ACTIONS.contains(normalized);
    }
}
