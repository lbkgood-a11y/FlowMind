package com.triobase.common.action.util;

import com.triobase.common.core.util.StringHelpers;

public final class ActionHelpers {

    private ActionHelpers() {
    }

    /**
     * @deprecated Use {@link StringHelpers#firstNonBlank(String...)} directly.
     */
    @Deprecated
    public static String firstNonBlank(String... values) {
        return StringHelpers.firstNonBlank(values);
    }

    /**
     * Whether an error code indicates a server-side failure (as opposed to a
     * client-side rejection).
     */
    public static boolean isServerError(int code) {
        return code >= 50_000 || (code >= 500 && code < 600);
    }
}
