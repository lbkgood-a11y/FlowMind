package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.dto.CompiledRouteRelease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompiledReleaseCacheTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> values;

    @Test
    void usesTenantEnvironmentRouteAndReleaseInKeys() {
        CompiledReleaseCache cache = new CompiledReleaseCache(redis, new ObjectMapper());
        assertThat(cache.pointerKey("tenant-a", Environment.PROD, "orders.submit"))
                .isEqualTo("openapi:release:pointer:tenant-a:PROD:orders.submit");
        assertThat(cache.releaseKey("tenant-a", Environment.PROD, "orders.submit", "release-1"))
                .isEqualTo("openapi:release:compiled:tenant-a:PROD:orders.submit:release-1");
    }

    @Test
    void treatsRedisFailureAsCacheMissForPostgresFallback() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new IllegalStateException("redis unavailable"));
        CompiledReleaseCache cache = new CompiledReleaseCache(redis, new ObjectMapper());

        Optional<CompiledRouteRelease> result = cache.get("tenant-a", Environment.PROD, "orders.submit");

        assertThat(result).isEmpty();
    }
}
