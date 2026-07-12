package com.triobase.service.auth.service;

import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.auth.DataScopeProvider;
import com.triobase.service.auth.dto.DataPolicyDimensionResponse;
import com.triobase.service.auth.dto.DataPolicyResponse;
import com.triobase.service.auth.dto.EffectiveDataPolicyResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthDataScopeProvider implements DataScopeProvider {

    private final DataPolicyService dataPolicyService;

    public AuthDataScopeProvider(DataPolicyService dataPolicyService) {
        this.dataPolicyService = dataPolicyService;
    }

    @Override
    public DataScope resolve(String userId, String resourceCode, String actionCode) {
        EffectiveDataPolicyResponse effective = dataPolicyService.resolveEffective(userId, resourceCode, actionCode);
        return toDataScope(effective);
    }

    private DataScope toDataScope(EffectiveDataPolicyResponse effective) {
        if (effective == null) {
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
