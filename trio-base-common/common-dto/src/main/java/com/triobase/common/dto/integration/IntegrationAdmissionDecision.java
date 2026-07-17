package com.triobase.common.dto.integration;
public record IntegrationAdmissionDecision(boolean allowed,int httpStatus,String reason,String tenantId,String applicationClientId,
        String subscriptionId,long policyVersion,long retryAfterSeconds,long maxBodyBytes,long maxConcurrency,long maxActiveWorkflows) {
 public static IntegrationAdmissionDecision deny(int status,String reason,long retryAfter){return new IntegrationAdmissionDecision(false,status,reason,null,null,null,0,retryAfter,0,0,0);}
}
