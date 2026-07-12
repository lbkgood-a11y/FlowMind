package com.triobase.common.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint as intentionally public.
 *
 * <p>Controllers should either declare a concrete {@link RequirePermission}
 * or explicitly opt out with this annotation. Future CI checks can use this
 * marker to detect authorization coverage gaps.</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
}
