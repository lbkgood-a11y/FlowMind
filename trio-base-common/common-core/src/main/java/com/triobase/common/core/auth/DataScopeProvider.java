package com.triobase.common.core.auth;

/**
 * Resolves the effective data scope for the current business query.
 */
public interface DataScopeProvider {

    DataScope resolve(String userId, String resourceCode, String actionCode);
}
