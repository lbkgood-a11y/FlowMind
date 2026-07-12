package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.auth.dto.DataPolicyDimensionRequest;
import com.triobase.service.auth.dto.DataPolicyResponse;
import com.triobase.service.auth.dto.EffectiveDataPolicyResponse;
import com.triobase.service.auth.dto.SaveDataPolicyRequest;
import com.triobase.service.auth.entity.SysDataPolicy;
import com.triobase.service.auth.entity.SysDataPolicyDimension;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.entity.SysUserRole;
import com.triobase.service.auth.mapper.DataPolicyDimensionMapper;
import com.triobase.service.auth.mapper.DataPolicyMapper;
import com.triobase.service.auth.mapper.OrgScopeMapper;
import com.triobase.service.auth.mapper.RoleMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DataPolicyService {

    private static final String DEFAULT_TENANT = "default";
    private static final String SUBJECT_TYPE_ROLE = "ROLE";
    private static final Set<String> EFFECTS = Set.of("ALLOW", "DENY");
    private static final Set<String> COMBINE_MODES = Set.of("AND", "OR");
    private static final Set<String> SCOPE_TYPES = Set.of(
            "SELF",
            "OWN_ORG",
            "OWN_ORG_AND_CHILDREN",
            "ASSIGNED_ORGS",
            "ALL"
    );

    private final DataPolicyMapper dataPolicyMapper;
    private final DataPolicyDimensionMapper dataPolicyDimensionMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final OrgScopeMapper orgScopeMapper;

    public List<DataPolicyResponse> listByRole(String roleId) {
        List<SysDataPolicy> policies = dataPolicyMapper.selectList(new LambdaQueryWrapper<SysDataPolicy>()
                .eq(SysDataPolicy::getTenantId, DEFAULT_TENANT)
                .eq(SysDataPolicy::getSubjectType, SUBJECT_TYPE_ROLE)
                .eq(StringUtils.hasText(roleId), SysDataPolicy::getSubjectId, roleId)
                .orderByDesc(SysDataPolicy::getCreatedAt));
        return toResponses(policies);
    }

    public DataPolicyResponse findById(String id) {
        SysDataPolicy policy = dataPolicyMapper.selectById(id);
        if (policy == null) {
            throw new BizException(40461, "DATA_POLICY_NOT_FOUND");
        }
        return toResponse(policy);
    }

    @Transactional
    public DataPolicyResponse create(SaveDataPolicyRequest request) {
        validateRequest(request);
        SysDataPolicy policy = new SysDataPolicy();
        policy.setId(UlidGenerator.nextUlid());
        policy.setTenantId(DEFAULT_TENANT);
        policy.setSubjectType(SUBJECT_TYPE_ROLE);
        fillPolicy(policy, request);
        dataPolicyMapper.insert(policy);
        replaceDimensions(policy.getId(), request.getDimensions());
        return findById(policy.getId());
    }

    @Transactional
    public DataPolicyResponse update(String id, SaveDataPolicyRequest request) {
        SysDataPolicy policy = dataPolicyMapper.selectById(id);
        if (policy == null) {
            throw new BizException(40461, "DATA_POLICY_NOT_FOUND");
        }
        validateRequest(request);
        fillPolicy(policy, request);
        dataPolicyMapper.updateById(policy);
        replaceDimensions(policy.getId(), request.getDimensions());
        return findById(policy.getId());
    }

    @Transactional
    public void delete(String id) {
        if (dataPolicyMapper.selectById(id) == null) {
            throw new BizException(40461, "DATA_POLICY_NOT_FOUND");
        }
        dataPolicyDimensionMapper.delete(new LambdaQueryWrapper<SysDataPolicyDimension>()
                .eq(SysDataPolicyDimension::getPolicyId, id));
        dataPolicyMapper.deleteById(id);
    }

    public EffectiveDataPolicyResponse resolveEffective(String userId, String resourceCode, String actionCode) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(resourceCode) || !StringUtils.hasText(actionCode)) {
            throw new BizException(40061, "DATA_POLICY_QUERY_REQUIRED");
        }
        List<String> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .toList();

        List<SysDataPolicy> policies = roleIds.isEmpty()
                ? List.of()
                : dataPolicyMapper.selectList(new LambdaQueryWrapper<SysDataPolicy>()
                .eq(SysDataPolicy::getTenantId, DEFAULT_TENANT)
                .eq(SysDataPolicy::getSubjectType, SUBJECT_TYPE_ROLE)
                .in(SysDataPolicy::getSubjectId, roleIds)
                .eq(SysDataPolicy::getResourceCode, resourceCode)
                .eq(SysDataPolicy::getActionCode, actionCode)
                .eq(SysDataPolicy::getStatus, (short) 1)
                .orderByDesc(SysDataPolicy::getEffect)
                .orderByAsc(SysDataPolicy::getSubjectId));

        EffectiveDataPolicyResponse response = new EffectiveDataPolicyResponse();
        response.setUserId(userId);
        response.setResourceCode(resourceCode);
        response.setActionCode(actionCode);
        response.setRoleIds(roleIds);
        List<DataPolicyResponse> policyResponses = toResponses(policies);
        boolean orgContextResolved = resolveOrgContext(userId, policyResponses);
        response.setPolicies(policyResponses);
        response.setRestrictive(policies.isEmpty());
        response.setOrgContextResolved(orgContextResolved);
        return response;
    }

    private boolean resolveOrgContext(String userId, List<DataPolicyResponse> policies) {
        boolean resolved = false;
        for (DataPolicyResponse policy : policies) {
            if (policy.getDimensions() == null || !"ALLOW".equalsIgnoreCase(policy.getEffect())) {
                continue;
            }
            for (var dimension : policy.getDimensions()) {
                String scopeType = dimension.getScopeType();
                if ("ASSIGNED_ORGS".equals(scopeType)) {
                    resolved = true;
                    continue;
                }
                if (!"OWN_ORG".equals(scopeType) && !"OWN_ORG_AND_CHILDREN".equals(scopeType)) {
                    continue;
                }
                List<String> ownOrgUnitIds = resolveOwnOrgUnitIds(userId, dimension.getDimensionCode());
                if ("OWN_ORG_AND_CHILDREN".equals(scopeType) && !ownOrgUnitIds.isEmpty()) {
                    String dimensionId = orgScopeMapper.selectDimensionId(DEFAULT_TENANT, dimension.getDimensionCode());
                    if (StringUtils.hasText(dimensionId)) {
                        ownOrgUnitIds = orgScopeMapper.selectOrgUnitAndDescendantIds(
                                DEFAULT_TENANT,
                                dimensionId,
                                ownOrgUnitIds
                        );
                    }
                }
                dimension.setOrgUnitIds(ownOrgUnitIds);
                resolved = true;
            }
        }
        return resolved;
    }

    private List<String> resolveOwnOrgUnitIds(String userId, String dimensionCode) {
        if (!StringUtils.hasText(dimensionCode)) {
            return List.of();
        }
        String dimensionId = orgScopeMapper.selectDimensionId(DEFAULT_TENANT, dimensionCode);
        if (!StringUtils.hasText(dimensionId)) {
            return List.of();
        }
        List<String> orgUnitIds = orgScopeMapper.selectActiveUserOrgUnitIds(DEFAULT_TENANT, dimensionId, userId);
        if (orgUnitIds.isEmpty()) {
            return List.of();
        }
        return List.of(orgUnitIds.get(0));
    }

    private void fillPolicy(SysDataPolicy policy, SaveDataPolicyRequest request) {
        policy.setSubjectType(SUBJECT_TYPE_ROLE);
        policy.setSubjectId(request.getRoleId().trim());
        policy.setResourceCode(request.getResourceCode().trim().toUpperCase(Locale.ROOT));
        policy.setActionCode(request.getActionCode().trim().toUpperCase(Locale.ROOT));
        policy.setEffect(normalizeEnum(request.getEffect(), "ALLOW", EFFECTS, "DATA_POLICY_EFFECT_INVALID"));
        policy.setCombineMode(normalizeEnum(request.getCombineMode(), "AND", COMBINE_MODES, "DATA_POLICY_COMBINE_MODE_INVALID"));
        policy.setStatus(toStatus(request.getStatus()));
        policy.setDescription(normalizeBlank(request.getDescription()));
    }

    private void validateRequest(SaveDataPolicyRequest request) {
        if (!StringUtils.hasText(request.getRoleId())
                || !StringUtils.hasText(request.getResourceCode())
                || !StringUtils.hasText(request.getActionCode())) {
            throw new BizException(40062, "DATA_POLICY_REQUIRED");
        }
        SysRole role = roleMapper.selectById(request.getRoleId());
        if (role == null) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND);
        }
        if (request.getDimensions() == null || request.getDimensions().isEmpty()) {
            throw new BizException(40063, "DATA_POLICY_DIMENSIONS_REQUIRED");
        }
        Set<String> dimensionCodes = new LinkedHashSet<>();
        for (DataPolicyDimensionRequest dimension : request.getDimensions()) {
            String dimensionCode = normalizeBlank(dimension.getDimensionCode());
            String scopeType = normalizeEnum(dimension.getScopeType(), null, SCOPE_TYPES, "DATA_POLICY_SCOPE_INVALID");
            if (dimensionCode == null) {
                throw new BizException(40064, "DATA_POLICY_DIMENSION_REQUIRED");
            }
            if (!dimensionCodes.add(dimensionCode.toUpperCase(Locale.ROOT))) {
                throw new BizException(40065, "DATA_POLICY_DIMENSION_DUPLICATE");
            }
            if ("ASSIGNED_ORGS".equals(scopeType)
                    && (dimension.getOrgUnitIds() == null || normalizeOrgUnitIds(dimension.getOrgUnitIds()).isEmpty())) {
                throw new BizException(40066, "DATA_POLICY_ASSIGNED_ORGS_REQUIRED");
            }
        }
    }

    private void replaceDimensions(String policyId, List<DataPolicyDimensionRequest> dimensions) {
        dataPolicyDimensionMapper.delete(new LambdaUpdateWrapper<SysDataPolicyDimension>()
                .eq(SysDataPolicyDimension::getPolicyId, policyId));
        int index = 0;
        for (DataPolicyDimensionRequest request : dimensions) {
            SysDataPolicyDimension dimension = new SysDataPolicyDimension();
            dimension.setId(UlidGenerator.nextUlid());
            dimension.setPolicyId(policyId);
            dimension.setDimensionCode(request.getDimensionCode().trim().toUpperCase(Locale.ROOT));
            dimension.setScopeType(normalizeEnum(request.getScopeType(), null, SCOPE_TYPES, "DATA_POLICY_SCOPE_INVALID"));
            dimension.setOrgUnitIds(String.join(",", normalizeOrgUnitIds(request.getOrgUnitIds())));
            dimension.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : (index + 1) * 10);
            dataPolicyDimensionMapper.insert(dimension);
            index++;
        }
    }

    private List<DataPolicyResponse> toResponses(List<SysDataPolicy> policies) {
        if (policies.isEmpty()) {
            return List.of();
        }
        List<String> policyIds = policies.stream().map(SysDataPolicy::getId).toList();
        List<SysDataPolicyDimension> dimensions = dataPolicyDimensionMapper.selectList(
                new LambdaQueryWrapper<SysDataPolicyDimension>()
                        .in(SysDataPolicyDimension::getPolicyId, policyIds)
                        .orderByAsc(SysDataPolicyDimension::getSortOrder));
        Map<String, List<SysDataPolicyDimension>> dimensionsByPolicy = dimensions.stream()
                .collect(Collectors.groupingBy(SysDataPolicyDimension::getPolicyId));
        return policies.stream()
                .map(policy -> DataPolicyResponse.from(policy, dimensionsByPolicy.getOrDefault(policy.getId(), List.of())))
                .toList();
    }

    private DataPolicyResponse toResponse(SysDataPolicy policy) {
        List<SysDataPolicyDimension> dimensions = dataPolicyDimensionMapper.selectList(
                new LambdaQueryWrapper<SysDataPolicyDimension>()
                        .eq(SysDataPolicyDimension::getPolicyId, policy.getId())
                        .orderByAsc(SysDataPolicyDimension::getSortOrder));
        return DataPolicyResponse.from(policy, dimensions);
    }

    private List<String> normalizeOrgUnitIds(List<String> orgUnitIds) {
        if (orgUnitIds == null) {
            return List.of();
        }
        return orgUnitIds.stream()
                .map(this::normalizeBlank)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
    }

    private String normalizeEnum(String value, String defaultValue, Set<String> allowed, String errorMessage) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            normalized = defaultValue;
        }
        if (normalized == null) {
            throw new BizException(40067, errorMessage);
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new BizException(40067, errorMessage);
        }
        return normalized;
    }

    private Short toStatus(Integer status) {
        return status != null && status == 0 ? (short) 0 : (short) 1;
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
