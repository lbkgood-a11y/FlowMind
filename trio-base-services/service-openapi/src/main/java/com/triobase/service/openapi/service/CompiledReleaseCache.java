package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.dto.CompiledRouteRelease;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class CompiledReleaseCache {

    private static final Duration TTL = Duration.ofMinutes(30);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Autowired
    public CompiledReleaseCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                                MeterRegistry meterRegistry) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public CompiledReleaseCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this(redis, objectMapper, new SimpleMeterRegistry());
    }

    public Optional<CompiledRouteRelease> get(String tenantId, Environment environment, String routeKey) {
        try {
            String releaseId = redis.opsForValue().get(pointerKey(tenantId, environment, routeKey));
            if (releaseId == null) {
                meterRegistry.counter("triobase.openapi.cache.miss", "cache", "release").increment();
                return Optional.empty();
            }
            String payload = redis.opsForValue().get(releaseKey(tenantId, environment, routeKey, releaseId));
            if (payload == null) {
                meterRegistry.counter("triobase.openapi.cache.miss", "cache", "release").increment();
                return Optional.empty();
            }
            meterRegistry.counter("triobase.openapi.cache.hit", "cache", "release").increment();
            return Optional.of(objectMapper.readValue(payload, CompiledRouteRelease.class));
        } catch (Exception exception) {
            meterRegistry.counter("triobase.openapi.cache.error", "operation", "read").increment();
            log.warn("Compiled release cache read failed tenant={} environment={} routeKey={}",
                    tenantId, environment, routeKey);
            return Optional.empty();
        }
    }

    public void put(CompiledRouteRelease release) {
        try {
            String pointer = pointerKey(release.tenantId(), release.environment(), release.routeKey());
            String immutable = releaseKey(release.tenantId(), release.environment(),
                    release.routeKey(), release.releaseId());
            redis.opsForValue().set(immutable, objectMapper.writeValueAsString(release), TTL);
            redis.opsForValue().set(pointer, release.releaseId(), TTL);
            meterRegistry.counter("triobase.openapi.cache.write", "cache", "release").increment();
        } catch (Exception exception) {
            meterRegistry.counter("triobase.openapi.cache.error", "operation", "write").increment();
            log.warn("Compiled release cache write failed tenant={} environment={} routeKey={}",
                    release.tenantId(), release.environment(), release.routeKey());
        }
    }

    public void evict(String tenantId, Environment environment, String routeKey) {
        try {
            redis.delete(pointerKey(tenantId, environment, routeKey));
            meterRegistry.counter("triobase.openapi.cache.eviction", "cache", "release").increment();
        } catch (Exception exception) {
            meterRegistry.counter("triobase.openapi.cache.error", "operation", "evict").increment();
            log.warn("Compiled release cache eviction failed tenant={} environment={} routeKey={}",
                    tenantId, environment, routeKey);
        }
    }

    String pointerKey(String tenantId, Environment environment, String routeKey) {
        return "openapi:release:pointer:" + tenant(tenantId) + ':' + environment + ':' + routeKey;
    }

    String releaseKey(String tenantId, Environment environment, String routeKey, String releaseId) {
        return "openapi:release:compiled:" + tenant(tenantId) + ':' + environment + ':' + routeKey + ':' + releaseId;
    }

    private String tenant(String tenantId) {
        return tenantId == null ? "__PLATFORM__" : tenantId;
    }
}
