package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.AuthErrorCode;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.auth.dto.DataPolicyDimensionRequest;
import com.triobase.service.auth.dto.DataPolicyDimensionResponse;
import com.triobase.service.auth.dto.DataPolicyResponse;
import com.triobase.service.auth.dto.EffectiveDataPolicyResponse;
import com.triobase.service.auth.dto.SaveDataPolicyRequest;
import com.triobase.service.auth.entity.SysAuthAction;
import com.triobase.service.auth.entity.SysAuthResource;
import com.triobase.service.auth.entity.SysDataPolicy;
import com.triobase.service.auth.entity.SysDataPolicyDimension;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.entity.SysUserRole;
import com.triobase.service.auth.mapper.AuthActionMapper;
import com.triobase.service.auth.mapper.AuthResourceMapper;
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
    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final String ADMIN_ALL_POLICY_ID = "SYSTEM_ADMIN_ALL";
    private static final String DEFAULT_DIMENSION_CODE = "ADMIN";
    private static final String ACTIVE = "ACTIVE";
    private static final short STATUS_ENABLED = 1;
    private static final Set<String> EFFECTS = Set.of("ALLOW", "DENY");
    private static final Set<String> COMBINE_MODES = Set.of("AND", "OR");
    private static final Set<String> SCOPE_TYPES = Set.of(
            "SELF",
            "OWN_ORG",
            "OWN_ORG_AND_CHILDREN",
            "ASSIGNED_ORGS",
            "PARTICIPATED",
            "CANDIDATE_TASKS",
            "ALL"
    );

    private final DataPolicyMapper dataPolicyMapper;
    private final DataPolicyDimensionMapper dataPolicyDimensionMapper;
    private final AuthResourceMapper authResourceMapper;
    private final AuthActionMapper authActionMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final OrgScopeMapper orgScopeMapper;

    public List<DataPolicyResponse> listByRole(String roleId) {
        List<SysDataPolicy> policies = dataPolicyMapper.selectList(new LambdaQueryWrapper<SysDataPolicy>()
                .eq(SysDataPolicy::getTenantId, currentTenantId())
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
        policy.setTenantId(currentTenantId());
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
        return resolveEffective(currentTenantId(), userId, resourceCode, actionCode);
    }

    public EffectiveDataPolicyResponse resolveEffective(String tenantId,
                                                        String userId,
                                                        String resourceCode,
                                                        String actionCode) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(resourceCode) || !StringUtils.hasText(actionCode)) {
            throw new BizException(40061, "DATA_POLICY_QUERY_REQUIRED");
        }
        String effectiveTenant = StringUtils.hasText(tenantId) ? tenantId.trim() : DEFAULT_TENANT;
        List<String> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .toList();
        String adminRoleId = findAdminRoleId(roleIds);
        if (StringUtils.hasText(adminRoleId)) {
            return adminAllResponse(userId, resourceCode, actionCode, roleIds, adminRoleId);
        }

        List<SysDataPolicy> policies = roleIds.isEmpty()
                ? List.of()
                : dataPolicyMapper.selectList(new LambdaQueryWrapper<SysDataPolicy>()
                .eq(SysDataPolicy::getTenantId, effectiveTenant)
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
        boolean orgContextResolved = resolveOrgContext(effectiveTenant, userId, policyResponses);
        response.setPolicies(policyResponses);
        response.setRestrictive(policies.isEmpty());
        response.setOrgContextResolved(orgContextResolved);
        return response;
    }

    private String findAdminRoleId(List<String> roleIds) {
        if (roleIds.isEmpty()) {
            return null;
        }
        SysRole adminRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getRoleCode, ADMIN_ROLE_CODE)
                .eq(SysRole::getStatus, (short) 1)
                .last("LIMIT 1"));
        return adminRole != null ? adminRole.getId() : null;
    }

    private EffectiveDataPolicyResponse adminAllResponse(String userId,
                                                         String resourceCode,
                                                         String actionCode,
                                                         List<String> roleIds,
                                                         String adminRoleId) {
        DataPolicyDimensionResponse dimension = new DataPolicyDimensionResponse();
        dimension.setId(ADMIN_ALL_POLICY_ID + "_DIMENSION");
        dimension.setDimensionCode(DEFAULT_DIMENSION_CODE);
        dimension.setScopeType("ALL");
        dimension.setOrgUnitIds(List.of());
        dimension.setSortOrder(0);

        DataPolicyResponse policy = new DataPolicyResponse();
        policy.setId(ADMIN_ALL_POLICY_ID);
        policy.setRoleId(adminRoleId);
        policy.setResourceCode(resourceCode);
        policy.setActionCode(actionCode);
        policy.setEffect("ALLOW");
        policy.setCombineMode("AND");
        policy.setStatus((short) 1);
        policy.setDescription("超级管理员运行时全量数据范围");
        policy.setDimensions(List.of(dimension));

        EffectiveDataPolicyResponse response = new EffectiveDataPolicyResponse();
        response.setUserId(userId);
        response.setResourceCode(resourceCode);
        response.setActionCode(actionCode);
        response.setRoleIds(roleIds);
        response.setPolicies(List.of(policy));
        response.setRestrictive(false);
        response.setOrgContextResolved(true);
        return response;
    }

    private boolean resolveOrgContext(String tenantId, String userId, List<DataPolicyResponse> policies) {
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
                List<String> ownOrgUnitIds = resolveOwnOrgUnitIds(tenantId, userId, dimension.getDimensionCode());
                if ("OWN_ORG_AND_CHILDREN".equals(scopeType) && !ownOrgUnitIds.isEmpty()) {
                    String dimensionId = orgScopeMapper.selectDimensionId(tenantId, dimension.getDimensionCode());
                    if (StringUtils.hasText(dimensionId)) {
                        ownOrgUnitIds = orgScopeMapper.selectOrgUnitAndDescendantIds(
                                tenantId,
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

    private List<String> resolveOwnOrgUnitIds(String tenantId, String userId, String dimensionCode) {
        if (!StringUtils.hasText(dimensionCode)) {
            return List.of();
        }
        String dimensionId = orgScopeMapper.selectDimensionId(tenantId, dimensionCode);
        if (!StringUtils.hasText(dimensionId)) {
            return List.of();
        }
        List<String> orgUnitIds = orgScopeMapper.selectActiveUserOrgUnitIds(tenantId, dimensionId, userId);
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
        if (role == null || role.getStatus() == null || role.getStatus() != STATUS_ENABLED) {
            throw new BizException(AuthErrorCode.ROLE_NOT_FOUND);
        }
        ensureActionRegistered(
                currentTenantId(),
                request.getResourceCode().trim().toUpperCase(Locale.ROOT),
                request.getActionCode().trim().toUpperCase(Locale.ROOT)
        );
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

    private void ensureActionRegistered(String tenantId, String resourceCode, String actionCode) {
        Long resourceCount = authResourceMapper.selectCount(new LambdaQueryWrapper<SysAuthResource>()
                .eq(SysAuthResource::getTenantId, tenantId)
                .eq(SysAuthResource::getResourceCode, resourceCode)
                .eq(SysAuthResource::getLifecycleStatus, ACTIVE));
        Long actionCount = authActionMapper.selectCount(new LambdaQueryWrapper<SysAuthAction>()
                .eq(SysAuthAction::getTenantId, tenantId)
                .eq(SysAuthAction::getResourceCode, resourceCode)
                .eq(SysAuthAction::getActionCode, actionCode)
                .eq(SysAuthAction::getStatus, STATUS_ENABLED));
        if (resourceCount == null || resourceCount == 0 || actionCount == null || actionCount == 0) {
            throw new BizException(40468, "DATA_POLICY_ACTION_NOT_REGISTERED");
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

    private String currentTenantId() {
        String tenantId = SecurityContextHolder.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : DEFAULT_TENANT;
    }
}
