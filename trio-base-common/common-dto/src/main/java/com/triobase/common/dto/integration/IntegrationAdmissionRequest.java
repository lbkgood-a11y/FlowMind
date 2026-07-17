package com.triobase.common.dto.integration;
import java.util.Set;
public record IntegrationAdmissionRequest(String tenantId,String clientKey,String environment,String routeKey,String operation,
        String credential,String sourceIp,long contentLength,boolean tls,Set<String> scopes,
        String timestamp,String nonce,String signature,String bodyHash,String clientCertificateFingerprint,boolean callback) {
    public IntegrationAdmissionRequest(String tenantId,String clientKey,String environment,String routeKey,String operation,
            String credential,String sourceIp,long contentLength,boolean tls,Set<String> scopes,
            String timestamp,String nonce,String signature,String bodyHash,String clientCertificateFingerprint) {
        this(tenantId,clientKey,environment,routeKey,operation,credential,sourceIp,contentLength,tls,scopes,
                timestamp,nonce,signature,bodyHash,clientCertificateFingerprint,false);
    }
}
