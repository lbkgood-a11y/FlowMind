package com.triobase.service.openapi.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiTemporalWorkerConfigTest {

    @Test
    void requiresTaskQueueToMatchApplicationName() {
        assertThatCode(() -> new OpenApiTemporalWorkerConfig(
                "service-openapi", "service-openapi", 10, 5).validateTaskQueueBinding())
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new OpenApiTemporalWorkerConfig(
                "service-openapi", "shared-worker", 10, 5).validateTaskQueueBinding())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OPENAPI_TEMPORAL_TASK_QUEUE_MUST_MATCH_APPLICATION_NAME");
    }
}
