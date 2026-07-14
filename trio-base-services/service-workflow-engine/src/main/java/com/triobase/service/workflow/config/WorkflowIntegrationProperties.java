package com.triobase.service.workflow.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "workflow")
public class WorkflowIntegrationProperties {

    private final Services services = new Services();
    private final Internal internal = new Internal();
    private final Participants participants = new Participants();

    @Data
    public static class Services {
        private String authUrl = "http://localhost:8081";
        private String orgUrl = "http://localhost:8082";
        private String lowcodeUrl = "http://localhost:8085";
    }

    @Data
    public static class Internal {
        private String serviceName = "service-workflow-engine";
        private String token = "triobase-local-internal-token";
    }

    @Data
    public static class Participants {
        private Duration connectTimeout = Duration.ofMillis(200);
        private Duration readTimeout = Duration.ofMillis(400);
        private int maxCandidates = 200;
    }
}
