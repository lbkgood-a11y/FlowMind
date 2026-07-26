package com.triobase.common.core.util;

public final class StringHelpers {

    private StringHelpers() {
    }

    /**
     * Returns the first non-blank value after trimming, or {@code null} if all values are blank.
     */
    public static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * Returns the trimmed value if it has text, otherwise {@code null}.
     */
    public static String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
