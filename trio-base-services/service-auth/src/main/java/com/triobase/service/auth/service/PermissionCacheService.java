package com.triobase.service.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class PermissionCacheService {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "perm:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AuthorizationVersionService versionService;

    public PermissionCacheService(StringRedisTemplate redis,
                                   ObjectMapper objectMapper,
                                   AuthorizationVersionService versionService) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.versionService = versionService;
    }

    public Optional<CachedPermissionSet> get(String userId) {
        try {
            String raw = redis.opsForValue().get(KEY_PREFIX + userId);
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

    public void put(String userId, List<String> roles, List<String> permissions,
                    List<String> deniedPermissions) {
        try {
            long grantVersion = versionService.current(AuthorizationVersionService.GRANT);
            CachedPermissionSet cached = new CachedPermissionSet(
                    roles, permissions, deniedPermissions, grantVersion);
            redis.opsForValue().set(KEY_PREFIX + userId,
                    objectMapper.writeValueAsString(cached), TTL);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("Permission cache write failed; continuing without cache userId={}", userId, e);
        }
    }

    public void evict(String userId) {
        try {
            redis.delete(KEY_PREFIX + userId);
        } catch (RuntimeException e) {
            log.warn("Permission cache eviction failed userId={}", userId, e);
        }
    }

    public record CachedPermissionSet(
            List<String> roles,
            List<String> permissions,
            List<String> deniedPermissions,
            long grantVersion) {
    }
}
