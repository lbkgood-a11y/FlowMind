package com.triobase.data.analytics.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "data")
public class DataAnalyticsProperties {
    private Query query = new Query();
    private Embedding embedding = new Embedding();
    private Chunking chunking = new Chunking();

    @Data
    public static class Query {
        private int maxPageSize = 100;
        private int maxTopK = 20;
        private double similarityThreshold = 0.05;
    }

    @Data
    public static class Embedding {
        private int dimension = 32;
    }

    @Data
    public static class Chunking {
        private int maxChars = 500;
        private int overlapChars = 50;
    }
}
