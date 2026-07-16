package com.triobase.service.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiApplicationTest {

    @Test
    void applicationDeclaresSpringBootEntryPoint() {
        assertThat(OpenApiApplication.class).hasAnnotation(SpringBootApplication.class);
    }
}
