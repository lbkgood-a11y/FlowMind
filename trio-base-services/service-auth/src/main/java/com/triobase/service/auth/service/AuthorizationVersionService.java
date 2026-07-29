package com.triobase.service.auth.service;

import com.triobase.service.auth.entity.SysAuthVersion;
import com.triobase.service.auth.mapper.AuthVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
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
        try {
            SysAuthVersion version = authVersionMapper.selectById(key);
            if (version != null && version.getVersionValue() != null) {
                fallbackVersions.put(key, version.getVersionValue());
                return version.getVersionValue();
            }
        } catch (Exception e) {
            log.warn("Failed to read auth version for key={} from DB — falling back to in-memory value", key, e);
        }
        return fallbackVersions.computeIfAbsent(key, ignored -> 1L);
    }

    public long bump(String key) {
        authVersionMapper.bumpAtomic(key);
        fallbackVersions.merge(key, 1L, Long::sum);
        return current(key);
    }

    public long bumpIfExpected(String key, long expectedVersion) {
        int updated = authVersionMapper.bumpIfExpected(key, expectedVersion);
        if (updated != 1) {
            return -1L;
        }
        fallbackVersions.compute(key, (ignored, current) ->
                current == null ? expectedVersion + 1 : Math.max(current, expectedVersion + 1));
        return expectedVersion + 1;
    }
}
