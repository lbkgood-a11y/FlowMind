package com.triobase.common.openapi.resolution;

import com.triobase.common.openapi.dto.CompiledRouteRelease;
import com.triobase.common.openapi.enums.Environment;

/**
 * Resolves the active compiled route release for a tenant/route/environment
 * combination. Implemented by the management service; consumed by the runtime
 * service to decouple the two.
 */
@FunctionalInterface
public interface ReleaseResolver {

    CompiledRouteRelease resolveActive(String tenantId, String routeKey, Environment environment);
}
