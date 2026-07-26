package com.triobase.common.openapi.resolution;

import com.triobase.common.openapi.entity.CallbackProfileVersion;
import com.triobase.common.openapi.enums.Environment;

/**
 * Resolves a published callback profile for webhook reception.
 * Implemented by the management service; consumed by the runtime service
 * to look up callback configurations without depending on
 * {@code CallbackProfileService} directly.
 */
@FunctionalInterface
public interface CallbackProfileResolver {

    CallbackProfileVersion resolvePublished(String callbackKey, String tenantId, Environment environment);
}
