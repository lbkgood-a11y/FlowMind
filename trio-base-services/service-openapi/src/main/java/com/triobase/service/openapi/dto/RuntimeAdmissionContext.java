package com.triobase.service.openapi.dto;

import com.triobase.service.openapi.domain.enums.Environment;

public record RuntimeAdmissionContext(
        String tenantId,
        Environment environment,
        String applicationClientId,
        String subscriptionId,
        long policyVersion,
        long maxConcurrency,
        long maxActiveWorkflows) {
}
