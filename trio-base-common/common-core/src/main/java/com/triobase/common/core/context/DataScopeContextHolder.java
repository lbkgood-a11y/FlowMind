package com.triobase.common.core.context;

import com.triobase.common.core.auth.DataScope;

/**
 * Request-scoped data permission context.
 */
public final class DataScopeContextHolder {

    private static final ThreadLocal<DataScope> CONTEXT = new ThreadLocal<>();

    private DataScopeContextHolder() {
    }

    public static void set(DataScope dataScope) {
        CONTEXT.set(dataScope);
    }

    public static DataScope get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
