package com.triobase.common.openapi.dto;

import com.triobase.common.openapi.enums.Environment;

public record RuntimeAdmissionContext(
        String tenantId,
        Environment environment,
        String applicationClientId,
        String subscriptionId,
        long policyVersion,
        long maxConcurrency,
        long maxActiveWorkflows) {
}
