package com.triobase.data.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.data.analytics.config.DataAnalyticsProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingAndChunkingServiceTest {

    @Test
    void embeddingIsDeterministicAndNormalized() {
        DataAnalyticsProperties properties = new DataAnalyticsProperties();
        properties.getEmbedding().setDimension(16);
        EmbeddingService service = new EmbeddingService(properties, new ObjectMapper());

        double[] first = service.embed("Finance review amount above 5000");
        double[] second = service.embed("Finance review amount above 5000");

        assertThat(first).containsExactly(second);
        double length = 0;
        for (double value : first) {
            length += value * value;
        }
        assertThat(Math.sqrt(length)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.000001));
    }

    @Test
    void chunkingUsesConfiguredOverlap() {
        DataAnalyticsProperties properties = new DataAnalyticsProperties();
        properties.getChunking().setMaxChars(50);
        properties.getChunking().setOverlapChars(5);
        ChunkingService service = new ChunkingService(properties);

        String content = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        List<String> chunks = service.split(content);

        assertThat(chunks).containsExactly(
                content.substring(0, 50),
                content.substring(45));
    }
}
