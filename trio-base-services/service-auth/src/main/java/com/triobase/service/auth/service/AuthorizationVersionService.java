package com.triobase.service.auth.service;

import com.triobase.service.auth.entity.SysAuthVersion;
import com.triobase.service.auth.mapper.AuthVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护授权事实版本，用于缓存失效和决策证据关联。
 *
 * <p>数据库版本是跨实例事实；内存值只在数据库短暂不可用时保持当前进程可读，不能作为多个
 * service-auth 实例间的一致性来源。写入成功后必须递增对应事实类别。</p>
 */
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
            // 降级仅保留最近已知版本，不能把读取失败解释为版本回退或重新授权。
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
