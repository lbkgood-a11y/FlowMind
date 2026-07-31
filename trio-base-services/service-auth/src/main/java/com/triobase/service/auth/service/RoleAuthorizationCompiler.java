package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.authz.PageCapabilityManifestSyncRequest;
import com.triobase.service.auth.dto.RoleAuthorizationCompilationPlan;
import com.triobase.service.auth.entity.SysAuthPageCapability;
import com.triobase.service.auth.entity.SysAuthPageCapabilityTarget;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.entity.SysRoleAuthDraft;
import com.triobase.service.auth.entity.SysRoleAuthIntent;
import com.triobase.service.auth.mapper.AuthPageCapabilityMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityTargetMapper;
import com.triobase.service.auth.mapper.AuthPageCatalogMapper;
import com.triobase.service.auth.mapper.AuthFieldMapper;
import com.triobase.service.auth.entity.SysAuthField;
import com.triobase.service.auth.mapper.RoleAuthDraftMapper;
import com.triobase.service.auth.mapper.RoleAuthIntentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class RoleAuthorizationCompiler {

    private static final Set<String> SUPPORTED_SCOPE_TYPES = Set.of(
            "NONE", "SELF", "OWN_ORG", "OWN_ORG_AND_CHILDREN", "ASSIGNED_ORGS", "ALL");

    private final RoleAuthDraftMapper draftMapper;
    private final RoleAuthIntentMapper intentMapper;
    private final AuthPageCapabilityMapper capabilityMapper;
    private final AuthPageCapabilityTargetMapper targetMapper;
    private final AuthPageCatalogMapper catalogMapper;
    private final AuthFieldMapper fieldMapper;
    private final AuthorizationRegistryService authorizationRegistryService;
    private final ObjectMapper objectMapper;

    public RoleAuthorizationCompilationPlan compile(String requestedTenantId, String draftId) {
        String tenantId = authorizationRegistryService.effectiveTenant(requestedTenantId);
        SysRoleAuthDraft draft = draftMapper.selectOne(new LambdaQueryWrapper<SysRoleAuthDraft>()
                .eq(SysRoleAuthDraft::getTenantId, tenantId)
                .eq(SysRoleAuthDraft::getId, draftId));
        if (draft == null) {
            throw new BizException(40491, "ROLE_AUTH_DRAFT_NOT_FOUND");
        }
        SysAuthPageCatalog catalog = catalogMapper.selectOne(new LambdaQueryWrapper<SysAuthPageCatalog>()
                .eq(SysAuthPageCatalog::getTenantId, tenantId)
                .eq(SysAuthPageCatalog::getId, draft.getCatalogId()));
        if (catalog == null || !"ACTIVE".equals(catalog.getLifecycleStatus())) {
            throw new BizException(40998, "PAGE_CAPABILITY_ACTIVE_CATALOG_REQUIRED");
        }

        List<SysRoleAuthIntent> intents = intentMapper.selectList(new LambdaQueryWrapper<SysRoleAuthIntent>()
                .eq(SysRoleAuthIntent::getTenantId, tenantId)
                .eq(SysRoleAuthIntent::getDraftId, draftId));
        Map<String, SysAuthPageCapability> capabilities = loadCapabilities(tenantId, draft.getCatalogId(), intents);
        Map<String, List<SysAuthPageCapabilityTarget>> targets = loadTargets(tenantId, capabilities);

        TreeMap<String, RoleAuthorizationCompilationPlan.GrantProjection> grants = new TreeMap<>();
        TreeMap<String, RoleAuthorizationCompilationPlan.DataProjection> dataPolicies = new TreeMap<>();
        TreeMap<String, RoleAuthorizationCompilationPlan.FieldProjection> fieldPolicies = new TreeMap<>();
        TreeMap<String, RoleAuthorizationCompilationPlan.GuardProjection> guards = new TreeMap<>();

        Map<String, SysRoleAuthIntent> pageDefaultScopes = new LinkedHashMap<>();
        for (SysRoleAuthIntent intent : intents) {
            SysAuthPageCapability capability = capabilities.get(intent.getCapabilityId());
            if (capability != null && "READ".equals(capability.getCapabilityCategory())
                    && StringUtils.hasText(intent.getDefaultScopeType())) {
                pageDefaultScopes.putIfAbsent(capability.getPageCode(), intent);
            }
        }

        intents.stream()
                .sorted(Comparator.comparing(intent -> capabilities.get(intent.getCapabilityId()).getCapabilityCode()))
                .forEach(intent -> compileIntent(intent, capabilities.get(intent.getCapabilityId()),
                        targets.getOrDefault(intent.getCapabilityId(), List.of()),
                        pageDefaultScopes.get(capabilities.get(intent.getCapabilityId()).getPageCode()),
                        grants, dataPolicies, fieldPolicies, guards));

        RoleAuthorizationCompilationPlan plan = new RoleAuthorizationCompilationPlan();
        plan.setTenantId(tenantId);
        plan.setRoleId(draft.getRoleId());
        plan.setDraftId(draft.getId());
        plan.setCatalogId(catalog.getId());
        plan.setCatalogVersion(catalog.getCatalogVersion());
        plan.setIntentVersion(draft.getIntentVersion());
        plan.setBusinessSummary(StringUtils.hasText(draft.getValidationSummary())
                ? draft.getValidationSummary() : "该角色暂未获得任何页面功能");
        plan.setGrants(new ArrayList<>(grants.values()));
        plan.setDataPolicies(new ArrayList<>(dataPolicies.values()));
        plan.setFieldPolicies(new ArrayList<>(fieldPolicies.values()));
        plan.setGuards(new ArrayList<>(guards.values()));
        return plan;
    }

    private Map<String, SysAuthPageCapability> loadCapabilities(
            String tenantId, String catalogId, List<SysRoleAuthIntent> intents) {
        Map<String, SysAuthPageCapability> result = new LinkedHashMap<>();
        if (intents.isEmpty()) {
            return result;
        }
        capabilityMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapability>()
                        .eq(SysAuthPageCapability::getTenantId, tenantId)
                        .eq(SysAuthPageCapability::getCatalogId, catalogId)
                        .in(SysAuthPageCapability::getId,
                                intents.stream().map(SysRoleAuthIntent::getCapabilityId).toList()))
                .forEach(item -> result.put(item.getId(), item));
        if (result.size() != intents.size()) {
            throw new BizException(40998, "PAGE_CAPABILITY_SELECTION_INCOMPLETE");
        }
        result.values().forEach(item -> {
            if (!"READY".equals(item.getReadinessStatus()) || item.getStatus() == null || item.getStatus() != 1) {
                throw new BizException(40998, "页面功能“" + item.getCapabilityName() + "”尚未就绪");
            }
        });
        return result;
    }

    private Map<String, List<SysAuthPageCapabilityTarget>> loadTargets(
            String tenantId, Map<String, SysAuthPageCapability> capabilities) {
        Map<String, List<SysAuthPageCapabilityTarget>> result = new LinkedHashMap<>();
        if (capabilities.isEmpty()) {
            return result;
        }
        targetMapper.selectList(new LambdaQueryWrapper<SysAuthPageCapabilityTarget>()
                        .eq(SysAuthPageCapabilityTarget::getTenantId, tenantId)
                        .eq(SysAuthPageCapabilityTarget::getStatus, (short) 1)
                        .in(SysAuthPageCapabilityTarget::getCapabilityId, capabilities.keySet()))
                .forEach(item -> result.computeIfAbsent(item.getCapabilityId(), ignored -> new ArrayList<>()).add(item));
        return result;
    }

    private void compileIntent(
            SysRoleAuthIntent intent, SysAuthPageCapability capability,
            List<SysAuthPageCapabilityTarget> targets,
            SysRoleAuthIntent pageDefaultScope,
            Map<String, RoleAuthorizationCompilationPlan.GrantProjection> grants,
            Map<String, RoleAuthorizationCompilationPlan.DataProjection> dataPolicies,
            Map<String, RoleAuthorizationCompilationPlan.FieldProjection> fieldPolicies,
            Map<String, RoleAuthorizationCompilationPlan.GuardProjection> guards) {
        for (SysAuthPageCapabilityTarget target : targets) {
            if (!"GRANT".equals(target.getTargetKind())) {
                continue;
            }
            String grantKey = target.getResourceCode() + "\u0000" + target.getActionCode();
            grants.computeIfAbsent(grantKey, ignored -> grant(capability, target));
            String scopeType;
            String scopeIds;
            if (StringUtils.hasText(intent.getOperationScopeType())) {
                scopeType = intent.getOperationScopeType();
                scopeIds = intent.getOperationScopeIds();
            } else if ("OPERATION".equals(capability.getCapabilityCategory()) && pageDefaultScope != null) {
                scopeType = pageDefaultScope.getDefaultScopeType();
                scopeIds = pageDefaultScope.getDefaultScopeIds();
            } else {
                scopeType = intent.getDefaultScopeType();
                scopeIds = intent.getDefaultScopeIds();
            }
            if (StringUtils.hasText(scopeType)) {
                String normalizedScope = scopeType.trim().toUpperCase();
                List<String> organizationIds = readStringList(scopeIds);
                validateScope(capability, normalizedScope, organizationIds);
                RoleAuthorizationCompilationPlan.DataProjection projection = new RoleAuthorizationCompilationPlan.DataProjection();
                projection.setCapabilityCode(capability.getCapabilityCode());
                projection.setResourceCode(target.getResourceCode());
                projection.setActionCode(target.getActionCode());
                projection.setScopeType(normalizedScope);
                projection.setOrganizationIds(organizationIds);
                dataPolicies.put(grantKey, projection);
            }
            List<RoleAuthorizationCompilationPlan.FieldRuleIntent> fieldRules =
                    readFieldRules(intent.getFieldIntentJson());
            if (!fieldRules.isEmpty() && !Short.valueOf((short) 1).equals(capability.getFieldPolicySupported())) {
                throw new BizException(40995, "该页面功能尚未通过字段规则执行验证，不能发布字段限制");
            }
            for (RoleAuthorizationCompilationPlan.FieldRuleIntent rule : fieldRules) {
                validateFieldRule(target, rule);
                RoleAuthorizationCompilationPlan.FieldProjection projection = new RoleAuthorizationCompilationPlan.FieldProjection();
                projection.setCapabilityCode(capability.getCapabilityCode());
                projection.setResourceCode(target.getResourceCode());
                projection.setFieldKey(rule.getFieldKey());
                projection.setReadMode(rule.getReadMode());
                projection.setWriteMode(rule.getWriteMode());
                projection.setMaskStrategy(rule.getMaskStrategy());
                fieldPolicies.put(target.getResourceCode() + "\u0000" + rule.getFieldKey(), projection);
            }
            for (String guardCode : declaredGuards(capability)) {
                RoleAuthorizationCompilationPlan.GuardProjection projection = new RoleAuthorizationCompilationPlan.GuardProjection();
                projection.setCapabilityCode(capability.getCapabilityCode());
                projection.setResourceCode(target.getResourceCode());
                projection.setActionCode(target.getActionCode());
                projection.setGuardCode(guardCode);
                projection.setConstraintJson(intent.getConstraintIntentJson());
                guards.put(grantKey + "\u0000" + guardCode, projection);
            }
        }
    }

    private void validateScope(SysAuthPageCapability capability, String scopeType,
                               List<String> organizationIds) {
        if (!Short.valueOf((short) 1).equals(capability.getScopeSupported())) {
            throw new BizException(40995, "该页面功能不支持单独配置数据范围");
        }
        if (!SUPPORTED_SCOPE_TYPES.contains(scopeType)) {
            throw new BizException(40995, "数据范围包含不支持的选项");
        }
        if ("ASSIGNED_ORGS".equals(scopeType) && organizationIds.isEmpty()) {
            throw new BizException(40995, "指定组织数据范围至少需要选择一个组织");
        }
    }

    private void validateFieldRule(SysAuthPageCapabilityTarget target,
                                   RoleAuthorizationCompilationPlan.FieldRuleIntent rule) {
        if (rule == null || !StringUtils.hasText(rule.getFieldKey())
                || !List.of("VISIBLE", "MASKED", "HIDDEN").contains(rule.getReadMode())
                || !List.of("WRITABLE", "READ_ONLY", "DENIED").contains(rule.getWriteMode())
                || ("MASKED".equals(rule.getReadMode()) && !StringUtils.hasText(rule.getMaskStrategy()))) {
            throw new BizException(40995, "字段保护规则不完整或包含不支持的选项");
        }
        Long registered = fieldMapper.selectCount(new LambdaQueryWrapper<SysAuthField>()
                .eq(SysAuthField::getTenantId, target.getTenantId())
                .eq(SysAuthField::getResourceCode, target.getResourceCode())
                .eq(SysAuthField::getFieldKey, rule.getFieldKey())
                .eq(SysAuthField::getStatus, (short) 1));
        if (registered == null || registered == 0) {
            throw new BizException(40995, "所选字段尚未由业务服务登记，不能发布字段限制");
        }
    }

    private RoleAuthorizationCompilationPlan.GrantProjection grant(
            SysAuthPageCapability capability, SysAuthPageCapabilityTarget target) {
        RoleAuthorizationCompilationPlan.GrantProjection projection =
                new RoleAuthorizationCompilationPlan.GrantProjection();
        projection.setCapabilityCode(capability.getCapabilityCode());
        projection.setResourceCode(target.getResourceCode());
        projection.setActionCode(target.getActionCode());
        projection.setEffect("ALLOW");
        return projection;
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new BizException(40995, "数据范围定义无法读取");
        }
    }

    private List<RoleAuthorizationCompilationPlan.FieldRuleIntent> readFieldRules(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new BizException(40995, "字段限制定义无法读取");
        }
    }

    private List<String> declaredGuards(SysAuthPageCapability capability) {
        if (!StringUtils.hasText(capability.getMetadataJson())) {
            return List.of();
        }
        try {
            PageCapabilityManifestSyncRequest.Capability manifest = objectMapper.readValue(
                    capability.getMetadataJson(), PageCapabilityManifestSyncRequest.Capability.class);
            return manifest.getGuardCodes() != null ? manifest.getGuardCodes().stream().sorted().toList() : List.of();
        } catch (JsonProcessingException exception) {
            throw new BizException(40995, "页面功能定义无法读取");
        }
    }
}
