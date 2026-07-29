package com.triobase.service.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class PermissionCacheService {

    private static final String KEY_PREFIX = "perm:";

    private final Duration ttl;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AuthorizationVersionService versionService;

    public PermissionCacheService(StringRedisTemplate redis,
                                   ObjectMapper objectMapper,
                                   AuthorizationVersionService versionService,
                                   @Value("${triobase.auth.permission-cache-ttl-minutes:5}") int ttlMinutes) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.versionService = versionService;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public Optional<CachedPermissionSet> get(String tenantId, String userId) {
        try {
            String raw = redis.opsForValue().get(cacheKey(tenantId, userId));
            if (raw == null) {
                return Optional.empty();
            }
            CachedPermissionSet cached = objectMapper.readValue(raw, CachedPermissionSet.class);
            long currentGrantVersion = versionService.current(AuthorizationVersionService.GRANT);
            if (cached.grantVersion() != currentGrantVersion) {
                return Optional.empty();
            }
            return Optional.of(cached);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Permission cache read failed; falling back to database userId={}", userId, e);
            return Optional.empty();
        }
    }

    public void put(String tenantId, String userId, List<String> roles, List<String> permissions,
                    List<String> deniedPermissions) {
        try {
            long grantVersion = versionService.current(AuthorizationVersionService.GRANT);
            CachedPermissionSet cached = new CachedPermissionSet(
                    roles, permissions, deniedPermissions, grantVersion);
            redis.opsForValue().set(cacheKey(tenantId, userId),
                    objectMapper.writeValueAsString(cached), ttl);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Permission cache write failed; continuing without cache userId={}", userId, e);
        }
    }

    public void evict(String tenantId, String userId) {
        try {
            redis.delete(cacheKey(tenantId, userId));
        } catch (RuntimeException e) {
            log.warn("Permission cache eviction failed userId={}", userId, e);
        }
    }

    private String cacheKey(String tenantId, String userId) {
        return KEY_PREFIX + tenantId + ":" + userId;
    }

    public record CachedPermissionSet(
            List<String> roles,
            List<String> permissions,
            List<String> deniedPermissions,
            long grantVersion) {
    }
}
