package com.triobase.service.apiruntime.resolution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.openapi.entity.CallbackProfileVersion;
import com.triobase.common.openapi.enums.Environment;
import com.triobase.common.openapi.enums.VersionLifecycleState;
import com.triobase.common.openapi.resolution.CallbackProfileResolver;
import com.triobase.service.apiruntime.infrastructure.mapper.CallbackProfileVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RuntimeCallbackProfileResolver implements CallbackProfileResolver {

    private final JdbcTemplate jdbcTemplate;
    private final CallbackProfileVersionMapper versionMapper;

    @Override
    public CallbackProfileVersion resolvePublished(String callbackKey, String tenantId, Environment environment) {
        List<String> profileIds = jdbcTemplate.queryForList("""
                SELECT id
                FROM oa_callback_profile
                WHERE callback_key = ?
                  AND tenant_id = ?
                  AND lifecycle_state = 'ACTIVE'
                LIMIT 1
                """, String.class, callbackKey, tenantId);
        if (profileIds.isEmpty()) {
            throw new BizException(40470, "OPENAPI_CALLBACK_PROFILE_NOT_FOUND");
        }
        CallbackProfileVersion version = versionMapper.selectOne(
                new LambdaQueryWrapper<CallbackProfileVersion>()
                        .eq(CallbackProfileVersion::getCallbackProfileId, profileIds.getFirst())
                        .eq(CallbackProfileVersion::getEnvironment, environment)
                        .eq(CallbackProfileVersion::getLifecycleState, VersionLifecycleState.PUBLISHED)
                        .orderByDesc(CallbackProfileVersion::getVersionNumber)
                        .last("LIMIT 1"));
        if (version == null) {
            throw new BizException(40471, "OPENAPI_CALLBACK_PROFILE_VERSION_NOT_FOUND");
        }
        return version;
    }
}
