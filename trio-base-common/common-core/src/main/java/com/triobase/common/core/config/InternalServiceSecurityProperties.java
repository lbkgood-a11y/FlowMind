package com.triobase.common.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "triobase.internal")
public class InternalServiceSecurityProperties {
    private boolean enabled = true;
    private String token = "triobase-local-internal-token";
    private List<String> allowedCallers = new ArrayList<>(List.of("service-workflow-engine"));
}
