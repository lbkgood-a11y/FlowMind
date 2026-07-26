package com.triobase.service.apiruntime.resolution;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.openapi.resolution.SubscriptionAccessChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RuntimeSubscriptionAccessChecker implements SubscriptionAccessChecker {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void requireRuntimeAccess(String applicationClientId, String subscriptionId,
                                     String routeKey, String operation, LocalDateTime at) {
        LocalDateTime effectiveAt = at == null ? LocalDateTime.now() : at;
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM oa_application_client c
                JOIN oa_product_subscription s ON s.application_client_id = c.id
                JOIN oa_api_product_route_member m
                  ON m.api_product_version_id = s.api_product_version_id
                 AND m.route_key = ?
                LEFT JOIN oa_subscription_route_override o
                  ON o.subscription_id = s.id
                 AND o.route_key = m.route_key
                WHERE c.id = ?
                  AND s.id = ?
                  AND c.lifecycle_state = 'ACTIVE'
                  AND s.lifecycle_state = 'ACTIVE'
                  AND s.environment = c.environment
                  AND s.tenant_id = c.tenant_id
                  AND (s.effective_from IS NULL OR s.effective_from <= ?)
                  AND (s.effective_until IS NULL OR s.effective_until > ?)
                  AND COALESCE(o.excluded, false) = false
                  AND (
                    ? IS NULL OR ? = ''
                    OR jsonb_array_length(
                      CASE
                        WHEN o.allowed_operations IS NOT NULL
                         AND jsonb_array_length(o.allowed_operations) > 0 THEN o.allowed_operations
                        ELSE m.operations
                      END
                    ) = 0
                    OR EXISTS (
                      SELECT 1
                      FROM jsonb_array_elements_text(
                        CASE
                          WHEN o.allowed_operations IS NOT NULL
                           AND jsonb_array_length(o.allowed_operations) > 0 THEN o.allowed_operations
                          ELSE m.operations
                        END
                      ) AS allowed(operation_name)
                      WHERE allowed.operation_name = ?
                    )
                  )
                """, Long.class,
                routeKey, applicationClientId, subscriptionId, effectiveAt, effectiveAt,
                operation, operation, operation);
        if (count == null || count < 1) {
            throw new BizException(40380, "OPENAPI_SUBSCRIPTION_ACCESS_DENIED");
        }
    }
}
