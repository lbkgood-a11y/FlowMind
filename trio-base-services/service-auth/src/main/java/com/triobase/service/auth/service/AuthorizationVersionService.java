package com.triobase.service.auth.service;

import com.triobase.service.auth.entity.SysAuthVersion;
import com.triobase.service.auth.mapper.AuthVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthorizationVersionService {

    public static final String AUTHORIZATION = "AUTHORIZATION";
    public static final String RESOURCE = "RESOURCE";
    public static final String GRANT = "GRANT";
    public static final String FIELD_POLICY = "FIELD_POLICY";
    public static final String GUARD_TEMPLATE = "GUARD_TEMPLATE";
    public static final String DATA_POLICY = "DATA_POLICY";

    private static final long CACHE_TTL_MS = 30_000;

    private final AuthVersionMapper authVersionMapper;
    private final Map<String, Long> fallbackVersions = new ConcurrentHashMap<>();
    private final Map<String, VersionEntry> versionCache = new ConcurrentHashMap<>();

    public long current(String key) {
        VersionEntry cached = versionCache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && (now - cached.fetchedAt) < CACHE_TTL_MS) {
            return cached.version;
        }

        SysAuthVersion version = authVersionMapper.selectById(key);
        if (version != null && version.getVersionValue() != null) {
            versionCache.put(key, new VersionEntry(version.getVersionValue(), now));
            return version.getVersionValue();
        }
        long fallback = fallbackVersions.computeIfAbsent(key, ignored -> 1L);
        versionCache.put(key, new VersionEntry(fallback, now));
        return fallback;
    }

    public long bump(String key) {
        versionCache.remove(key);
        int updated = authVersionMapper.bump(key);
        if (updated == 0) {
            SysAuthVersion version = new SysAuthVersion();
            version.setVersionKey(key);
            version.setVersionValue(1L);
            version.setUpdatedAt(LocalDateTime.now());
            authVersionMapper.insert(version);
        }
        fallbackVersions.merge(key, 1L, Long::sum);
        return current(key);
    }

    private record VersionEntry(long version, long fetchedAt) {}
}
