package com.triobase.service.auth.service;

import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.auth.DataScopeProvider;
import com.triobase.service.auth.dto.DataPolicyDimensionResponse;
import com.triobase.service.auth.dto.DataPolicyResponse;
import com.triobase.service.auth.dto.EffectiveDataPolicyResponse;
import org.springframework.stereotype.Component;

/**
 * 将 service-auth 的策略投影转换为通用 DataScope，供 Owner 服务执行数据过滤。
 *
 * <p>本组件只解析授权事实，不查询业务表。租户由显式参数或认证上下文确定；调用方必须把
 * restrictive、orgContextResolved 和 DENY 语义完整应用，不能只提取 ALLOW 组织集合。</p>
 */
@Component
public class AuthDataScopeProvider implements DataScopeProvider {

    private final DataPolicyService dataPolicyService;

    public AuthDataScopeProvider(DataPolicyService dataPolicyService) {
        this.dataPolicyService = dataPolicyService;
    }

    @Override
    public DataScope resolve(String userId, String resourceCode, String actionCode, String tenantId) {
        EffectiveDataPolicyResponse effective;
        if (tenantId != null && !tenantId.isBlank()) {
            effective = dataPolicyService.resolveEffective(tenantId, userId, resourceCode, actionCode);
        } else {
            effective = dataPolicyService.resolveEffective(userId, resourceCode, actionCode);
        }
        return toDataScope(effective);
    }

    private DataScope toDataScope(EffectiveDataPolicyResponse effective) {
        if (effective == null) {
            // null 表示没有可用的授权结论；跨服务调用方必须按未解析处理，禁止解释为允许全部。
            return null;
        }
        return new DataScope(
                effective.getUserId(),
                effective.getResourceCode(),
                effective.getActionCode(),
                effective.isRestrictive(),
                effective.isOrgContextResolved(),
                effective.getRoleIds(),
                effective.getPolicies() == null
                        ? java.util.List.of()
                        : effective.getPolicies().stream().map(this::toPolicy).toList()
        );
    }

    private DataScope.Policy toPolicy(DataPolicyResponse policy) {
        return new DataScope.Policy(
                policy.getId(),
                policy.getRoleId(),
                policy.getEffect(),
                policy.getCombineMode(),
                policy.getDimensions() == null
                        ? java.util.List.of()
                        : policy.getDimensions().stream().map(this::toDimension).toList()
        );
    }

    private DataScope.Dimension toDimension(DataPolicyDimensionResponse dimension) {
        return new DataScope.Dimension(
                dimension.getDimensionCode(),
                dimension.getScopeType(),
                dimension.getOrgUnitIds()
        );
    }
}
