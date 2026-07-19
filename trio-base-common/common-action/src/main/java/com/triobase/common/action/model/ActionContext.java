package com.triobase.common.action.model;

import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ActionContext {
    private String tenantId;
    private String traceId;
    private String requestId;
    private String correlationId;
    private String locale;
    private String clientIp;
    private String userAgent;
    private Long authVersion;
    private Long roleVersion;
    private Long dataPolicyVersion;
    private Long authorizationVersion;
    private Long fieldPolicyVersion;
    private Long guardTemplateVersion;
    private String confirmationId;
    private String confirmedBy;
    private Instant confirmedAt;
    private Map<String, Object> attributes = new LinkedHashMap<>();
}
