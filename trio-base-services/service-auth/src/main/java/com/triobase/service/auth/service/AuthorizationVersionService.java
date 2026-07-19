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

    private final AuthVersionMapper authVersionMapper;
    private final Map<String, Long> fallbackVersions = new ConcurrentHashMap<>();

    public long current(String key) {
        SysAuthVersion version = authVersionMapper.selectById(key);
        if (version != null && version.getVersionValue() != null) {
            return version.getVersionValue();
        }
        return fallbackVersions.computeIfAbsent(key, ignored -> 1L);
    }

    public long bump(String key) {
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
}
