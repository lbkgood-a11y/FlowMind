package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.authz.AuthorizationResourceSyncRequest;
import com.triobase.service.auth.dto.AuthorizationAdminOptionsResponse;
import com.triobase.service.auth.dto.AuthorizationGrantResponse;
import com.triobase.service.auth.dto.AuthorizationResourceResponse;
import com.triobase.service.auth.dto.AuthorizationResourceTreeResponse;
import com.triobase.service.auth.dto.AuthorizationSyncResponse;
import com.triobase.service.auth.dto.DecisionLogResponse;
import com.triobase.service.auth.dto.FieldPolicyResponse;
import com.triobase.service.auth.dto.GuardTemplateResponse;
import com.triobase.service.auth.dto.SaveAuthorizationGrantRequest;
import com.triobase.service.auth.dto.SaveFieldPolicyRequest;
import com.triobase.service.auth.dto.SaveGuardTemplateRequest;
import com.triobase.service.auth.entity.SysAuthAction;
import com.triobase.service.auth.entity.SysAuthDecisionLog;
import com.triobase.service.auth.entity.SysAuthField;
import com.triobase.service.auth.entity.SysAuthFieldPolicy;
import com.triobase.service.auth.entity.SysAuthGrant;
import com.triobase.service.auth.entity.SysAuthGuardTemplate;
import com.triobase.service.auth.entity.SysAuthResource;
import com.triobase.service.auth.mapper.AuthActionMapper;
import com.triobase.service.auth.mapper.AuthDecisionLogMapper;
import com.triobase.service.auth.mapper.AuthFieldMapper;
import com.triobase.service.auth.mapper.AuthFieldPolicyMapper;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.AuthGuardTemplateMapper;
import com.triobase.service.auth.mapper.AuthResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthorizationRegistryService {

    public static final String DEFAULT_TENANT = "default";
    private static final String ACTIVE = "ACTIVE";
    private static final Set<String> SUBJECT_TYPES = Set.of("ROLE", "USER");
    private static final Set<String> EFFECTS = Set.of("ALLOW", "DENY");

    private final AuthResourceMapper resourceMapper;
    private final AuthActionMapper actionMapper;
    private final AuthFieldMapper fieldMapper;
    private final AuthFieldPolicyMapper fieldPolicyMapper;
    private final AuthGuardTemplateMapper guardTemplateMapper;
    private final AuthGrantMapper grantMapper;
    private final AuthDecisionLogMapper decisionLogMapper;
    private final AuthorizationVersionService versionService;

    public PageResult<AuthorizationResourceResponse> pageResources(String tenantId,
                                                                   String ownerService,
                                                                   String resourceType,
                                                                   String keyword,
                                                                   int page,
                                                                   int size) {
        String effectiveTenant = effectiveTenant(tenantId);
        LambdaQueryWrapper<SysAuthResource> wrapper = new LambdaQueryWrapper<SysAuthResource>()
                .eq(SysAuthResource::getTenantId, effectiveTenant)
                .eq(StringUtils.hasText(ownerService), SysAuthResource::getOwnerService, ownerService)
                .eq(StringUtils.hasText(resourceType), SysAuthResource::getResourceType, normalize(resourceType))
                .and(StringUtils.hasText(keyword), q -> q
                        .like(SysAuthResource::getResourceCode, keyword)
                        .or()
                        .like(SysAuthResource::getDisplayName, keyword))
                .orderByAsc(SysAuthResource::getOwnerService)
                .orderByAsc(SysAuthResource::getResourceCode);
        IPage<SysAuthResource> result = resourceMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords().stream()
                .map(AuthorizationResourceResponse::from)
                .toList(), result.getTotal(), page, size);
    }

    public AuthorizationResourceTreeResponse resourceTree(String tenantId, String ownerService) {
        String effectiveTenant = effectiveTenant(tenantId);
        List<SysAuthResource> resources = resourceMapper.selectList(new LambdaQueryWrapper<SysAuthResource>()
                .eq(SysAuthResource::getTenantId, effectiveTenant)
                .eq(StringUtils.hasText(ownerService), SysAuthResource::getOwnerService, ownerService)
                .orderByAsc(SysAuthResource::getResourceType)
                .orderByAsc(SysAuthResource::getOwnerService)
                .orderByAsc(SysAuthResource::getResourceCode));
        List<String> resourceCodes = resources.stream()
                .map(SysAuthResource::getResourceCode)
                .toList();
        Map<String, List<SysAuthAction>> actionsByResource = resourceCodes.isEmpty()
                ? Map.of() : actionMapper.selectList(new LambdaQueryWrapper<SysAuthAction>()
                .eq(SysAuthAction::getTenantId, effectiveTenant)
                .in(SysAuthAction::getResourceCode, resourceCodes)
                .orderByAsc(SysAuthAction::getActionCategory)
                .orderByAsc(SysAuthAction::getActionCode))
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        SysAuthAction::getResourceCode, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        Map<String, List<SysAuthField>> fieldsByResource = resourceCodes.isEmpty()
                ? Map.of() : fieldMapper.selectList(new LambdaQueryWrapper<SysAuthField>()
                .eq(SysAuthField::getTenantId, effectiveTenant)
                .in(SysAuthField::getResourceCode, resourceCodes)
                .orderByAsc(SysAuthField::getFieldKey))
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        SysAuthField::getResourceCode, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        List<SysAuthGuardTemplate> guards = guardTemplateMapper.selectList(new LambdaQueryWrapper<SysAuthGuardTemplate>()
                .eq(SysAuthGuardTemplate::getTenantId, effectiveTenant)
                .eq(StringUtils.hasText(ownerService), SysAuthGuardTemplate::getOwnerService, ownerService)
                .orderByAsc(SysAuthGuardTemplate::getGuardCode));

        Map<String, List<AuthorizationResourceTreeResponse.ResourceNode>> nodesByType = new LinkedHashMap<>();
        for (SysAuthResource resource : resources) {
            AuthorizationResourceTreeResponse.ResourceNode node = resourceNode(
                    resource,
                    actionsByResource.getOrDefault(resource.getResourceCode(), List.of()),
                    fieldsByResource.getOrDefault(resource.getResourceCode(), List.of()),
                    guards);
            nodesByType.computeIfAbsent(resource.getResourceType(), ignored -> new ArrayList<>()).add(node);
        }

        AuthorizationResourceTreeResponse response = new AuthorizationResourceTreeResponse();
        response.setTenantId(effectiveTenant);
        response.setGroups(nodesByType.entrySet().stream()
                .map(entry -> resourceGroup(entry.getKey(), entry.getValue()))
                .toList());
        return response;
    }

    public AuthorizationAdminOptionsResponse adminOptions(String tenantId, String ownerService) {
        AuthorizationAdminOptionsResponse response = new AuthorizationAdminOptionsResponse();
        response.setFunctionActions(List.of(
                option("VIEW", "查看", "FUNCTION", "读取单据、页面或资源"),
                option("CREATE", "创建", "FUNCTION", "新建业务数据"),
                option("EDIT", "编辑", "FUNCTION", "修改已有业务数据"),
                option("DELETE", "删除", "FUNCTION", "删除或作废业务数据"),
                option("SUBMIT", "提交", "FUNCTION", "提交单据或发起流程"),
                option("APPROVE", "审批", "FUNCTION", "审批工作流任务"),
                option("REJECT", "驳回", "FUNCTION", "驳回工作流任务"),
                option("TRANSFER", "转办", "FUNCTION", "转交工作流任务"),
                option("EXPORT", "导出", "FUNCTION", "导出业务数据"),
                option("DESIGN", "设计", "LIFECYCLE", "设计低代码表单或应用"),
                option("PUBLISH", "发布", "LIFECYCLE", "发布低代码资源"),
                option("OFFLINE", "下线", "LIFECYCLE", "下线低代码资源")
        ));
        response.setDataScopes(List.of(
                option("SELF", "本人", "DATA_RANGE", "本人创建、提交或拥有的数据"),
                option("OWN_ORG", "本组织", "DATA_RANGE", "当前用户所属组织的数据"),
                option("OWN_ORG_AND_CHILDREN", "本组织及下级", "DATA_RANGE", "当前组织及其子组织的数据"),
                option("ASSIGNED_ORGS", "指定组织", "DATA_RANGE", "管理员指定组织集合的数据"),
                option("PARTICIPATED", "我参与的", "DATA_RANGE", "用户作为参与人的业务数据"),
                option("CANDIDATE_TASKS", "我的待办候选", "DATA_RANGE", "用户可处理的候选任务数据"),
                option("ALL", "全部", "DATA_RANGE", "当前租户内全部数据")
        ));
        response.setFieldReadModes(List.of(
                option("VISIBLE", "可见", "FIELD_READ", "字段原文可见"),
                option("MASKED", "脱敏", "FIELD_READ", "字段按策略脱敏后可见"),
                option("HIDDEN", "隐藏", "FIELD_READ", "字段不返回给调用方")
        ));
        response.setFieldWriteModes(List.of(
                option("EDITABLE", "可编辑", "FIELD_WRITE", "允许提交或修改字段"),
                option("READ_ONLY", "只读", "FIELD_WRITE", "返回时可见但不允许修改"),
                option("DENIED", "禁止写入", "FIELD_WRITE", "请求包含该字段时拒绝")
        ));
        response.setMaskStrategies(List.of(
                option("MASK", "固定掩码", "MASK", "使用固定占位符替换敏感值"),
                option("LAST4", "保留后四位", "MASK", "仅展示后四位"),
                option("PHONE", "手机号掩码", "MASK", "展示手机号前三后四"),
                option("EMAIL", "邮箱掩码", "MASK", "展示邮箱首字母和域名")
        ));
        response.setGuardTemplates(listGuardTemplates(tenantId, ownerService));
        return response;
    }

    public List<AuthorizationResourceResponse> staleResources(String tenantId,
                                                              String ownerService,
                                                              int staleMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(Math.max(staleMinutes, 1));
        return resourceMapper.selectList(new LambdaQueryWrapper<SysAuthResource>()
                        .eq(SysAuthResource::getTenantId, effectiveTenant(tenantId))
                        .eq(StringUtils.hasText(ownerService), SysAuthResource::getOwnerService, ownerService)
                        .lt(SysAuthResource::getLastSyncedAt, cutoff)
                        .orderByAsc(SysAuthResource::getOwnerService)
                        .orderByAsc(SysAuthResource::getResourceCode))
                .stream()
                .map(AuthorizationResourceResponse::from)
                .toList();
    }

    @Transactional
    public AuthorizationSyncResponse synchronize(AuthorizationResourceSyncRequest request) {
        if (request == null || !StringUtils.hasText(request.getOwnerService())
                || request.getResources() == null || request.getResources().isEmpty()) {
            throw new BizException(40080, "AUTHZ_SYNC_REQUIRED");
        }
        String tenantId = effectiveTenant(request.getTenantId());
        String ownerService = request.getOwnerService().trim();
        AuthorizationSyncResponse response = new AuthorizationSyncResponse();
        response.setTenantId(tenantId);
        response.setOwnerService(ownerService);
        Set<String> resources = new LinkedHashSet<>();
        int actions = 0;
        int fields = 0;
        int guards = 0;

        for (AuthorizationResourceSyncRequest.Resource resource : request.getResources()) {
            String resourceCode = required(resource.getResourceCode(), "AUTHZ_RESOURCE_CODE_REQUIRED");
            upsertResource(tenantId, ownerService, resource);
            resources.add(resourceCode);
            if (resource.getActions() != null) {
                for (AuthorizationResourceSyncRequest.Action action : resource.getActions()) {
                    upsertAction(tenantId, resourceCode, action);
                    actions++;
                }
            }
            if (resource.getFields() != null) {
                for (AuthorizationResourceSyncRequest.Field field : resource.getFields()) {
                    upsertField(tenantId, resourceCode, field);
                    fields++;
                }
            }
            if (resource.getGuards() != null) {
                for (AuthorizationResourceSyncRequest.Guard guard : resource.getGuards()) {
                    upsertGuard(tenantId, ownerService, guard);
                    guards++;
                }
            }
        }

        response.setResourceCodes(resources.stream().toList());
        response.setResourceCount(resources.size());
        response.setActionCount(actions);
        response.setFieldCount(fields);
        response.setGuardCount(guards);
        response.setResourceVersion(versionService.bump(AuthorizationVersionService.RESOURCE));
        if (guards > 0) {
            response.setGuardTemplateVersion(versionService.bump(AuthorizationVersionService.GUARD_TEMPLATE));
        } else {
            response.setGuardTemplateVersion(versionService.current(AuthorizationVersionService.GUARD_TEMPLATE));
        }
        versionService.bump(AuthorizationVersionService.AUTHORIZATION);
        return response;
    }

    public List<AuthorizationGrantResponse> listGrants(String tenantId,
                                                       String subjectType,
                                                       String subjectId,
                                                       String resourceCode) {
        return grantMapper.selectList(new LambdaQueryWrapper<SysAuthGrant>()
                        .eq(SysAuthGrant::getTenantId, effectiveTenant(tenantId))
                        .eq(StringUtils.hasText(subjectType), SysAuthGrant::getSubjectType, normalize(subjectType))
                        .eq(StringUtils.hasText(subjectId), SysAuthGrant::getSubjectId, subjectId)
                        .eq(StringUtils.hasText(resourceCode), SysAuthGrant::getResourceCode, normalizeResource(resourceCode))
                        .orderByAsc(SysAuthGrant::getSubjectType)
                        .orderByAsc(SysAuthGrant::getSubjectId)
                        .orderByAsc(SysAuthGrant::getResourceCode)
                        .orderByAsc(SysAuthGrant::getActionCode))
                .stream()
                .map(AuthorizationGrantResponse::from)
                .toList();
    }

    @Transactional
    public AuthorizationGrantResponse saveGrant(SaveAuthorizationGrantRequest request) {
        if (request == null) {
            throw new BizException(40081, "AUTHZ_GRANT_REQUIRED");
        }
        String tenantId = effectiveTenant(request.getTenantId());
        String subjectType = normalizeEnum(request.getSubjectType(), SUBJECT_TYPES, "AUTHZ_SUBJECT_TYPE_INVALID");
        String subjectId = required(request.getSubjectId(), "AUTHZ_SUBJECT_REQUIRED");
        String resourceCode = normalizeResource(required(request.getResourceCode(), "AUTHZ_RESOURCE_CODE_REQUIRED"));
        String actionCode = normalize(required(request.getActionCode(), "AUTHZ_ACTION_CODE_REQUIRED"));
        String effect = normalizeEnum(request.getEffect(), EFFECTS, "AUTHZ_GRANT_EFFECT_INVALID");
        ensureActionRegistered(tenantId, resourceCode, actionCode);

        SysAuthGrant grant = grantMapper.selectOne(new LambdaQueryWrapper<SysAuthGrant>()
                .eq(SysAuthGrant::getTenantId, tenantId)
                .eq(SysAuthGrant::getSubjectType, subjectType)
                .eq(SysAuthGrant::getSubjectId, subjectId)
                .eq(SysAuthGrant::getResourceCode, resourceCode)
                .eq(SysAuthGrant::getActionCode, actionCode)
                .eq(SysAuthGrant::getEffect, effect)
                .last("LIMIT 1"));
        if (grant == null) {
            grant = new SysAuthGrant();
            grant.setId(UlidGenerator.nextUlid());
            grant.setTenantId(tenantId);
            grant.setSubjectType(subjectType);
            grant.setSubjectId(subjectId);
            grant.setResourceCode(resourceCode);
            grant.setActionCode(actionCode);
            grant.setEffect(effect);
            grant.setStatus((short) 1);
            grant.setDescription(normalizeBlank(request.getDescription()));
            grantMapper.insert(grant);
        } else {
            grant.setStatus(toStatus(request.getStatus()));
            grant.setDescription(normalizeBlank(request.getDescription()));
            grantMapper.updateById(grant);
        }
        versionService.bump(AuthorizationVersionService.GRANT);
        versionService.bump(AuthorizationVersionService.AUTHORIZATION);
        return AuthorizationGrantResponse.from(grant);
    }

    @Transactional
    public void deleteGrant(String id) {
        if (!StringUtils.hasText(id) || grantMapper.selectById(id) == null) {
            throw new BizException(40480, "AUTHZ_GRANT_NOT_FOUND");
        }
        grantMapper.deleteById(id);
        versionService.bump(AuthorizationVersionService.GRANT);
        versionService.bump(AuthorizationVersionService.AUTHORIZATION);
    }

    private void upsertResource(String tenantId,
                                String ownerService,
                                AuthorizationResourceSyncRequest.Resource request) {
        String resourceCode = normalizeResource(required(request.getResourceCode(), "AUTHZ_RESOURCE_CODE_REQUIRED"));
        SysAuthResource resource = resourceMapper.selectOne(new LambdaQueryWrapper<SysAuthResource>()
                .eq(SysAuthResource::getTenantId, tenantId)
                .eq(SysAuthResource::getResourceCode, resourceCode)
                .last("LIMIT 1"));
        if (resource == null) {
            resource = new SysAuthResource();
            resource.setId(UlidGenerator.nextUlid());
            resource.setTenantId(tenantId);
            resource.setResourceCode(resourceCode);
        }
        resource.setResourceType(normalize(required(request.getResourceType(), "AUTHZ_RESOURCE_TYPE_REQUIRED")));
        resource.setOwnerService(ownerService);
        resource.setBusinessObjectId(normalizeBlank(request.getBusinessObjectId()));
        resource.setDisplayName(StringUtils.hasText(request.getDisplayName())
                ? request.getDisplayName().trim() : resourceCode);
        resource.setLifecycleStatus(StringUtils.hasText(request.getLifecycleStatus())
                ? normalize(request.getLifecycleStatus()) : ACTIVE);
        resource.setGlobalFlag(Boolean.TRUE.equals(request.getGlobalResource()) ? (short) 1 : (short) 0);
        resource.setMetadataJson(normalizeBlank(request.getMetadataJson()));
        resource.setLastSyncedAt(LocalDateTime.now());
        if (resource.getCreatedAt() == null && resourceMapper.selectById(resource.getId()) == null) {
            resourceMapper.insert(resource);
        } else {
            resourceMapper.updateById(resource);
        }
    }

    private void upsertAction(String tenantId,
                              String resourceCode,
                              AuthorizationResourceSyncRequest.Action request) {
        String actionCode = normalize(required(request.getActionCode(), "AUTHZ_ACTION_CODE_REQUIRED"));
        SysAuthAction action = actionMapper.selectOne(new LambdaQueryWrapper<SysAuthAction>()
                .eq(SysAuthAction::getTenantId, tenantId)
                .eq(SysAuthAction::getResourceCode, resourceCode)
                .eq(SysAuthAction::getActionCode, actionCode)
                .last("LIMIT 1"));
        if (action == null) {
            action = new SysAuthAction();
            action.setId(UlidGenerator.nextUlid());
            action.setTenantId(tenantId);
            action.setResourceCode(resourceCode);
            action.setActionCode(actionCode);
        }
        action.setActionCategory(StringUtils.hasText(request.getActionCategory())
                ? normalize(request.getActionCategory()) : "BUSINESS");
        action.setDescription(normalizeBlank(request.getDescription()));
        action.setGuardCodes(joinCodes(request.getGuardCodes()));
        action.setStatus(toStatus(request.getStatus()));
        if (action.getCreatedAt() == null && actionMapper.selectById(action.getId()) == null) {
            actionMapper.insert(action);
        } else {
            actionMapper.updateById(action);
        }
    }

    private void upsertField(String tenantId,
                             String resourceCode,
                             AuthorizationResourceSyncRequest.Field request) {
        String fieldKey = required(request.getFieldKey(), "AUTHZ_FIELD_KEY_REQUIRED");
        SysAuthField field = fieldMapper.selectOne(new LambdaQueryWrapper<SysAuthField>()
                .eq(SysAuthField::getTenantId, tenantId)
                .eq(SysAuthField::getResourceCode, resourceCode)
                .eq(SysAuthField::getFieldKey, fieldKey)
                .last("LIMIT 1"));
        if (field == null) {
            field = new SysAuthField();
            field.setId(UlidGenerator.nextUlid());
            field.setTenantId(tenantId);
            field.setResourceCode(resourceCode);
            field.setFieldKey(fieldKey);
        }
        field.setFieldLabel(normalizeBlank(request.getFieldLabel()));
        field.setFieldType(normalizeBlank(request.getFieldType()));
        field.setSensitivityClassification(normalizeBlank(request.getSensitivityClassification()));
        field.setDefaultMaskStrategy(normalizeBlank(request.getDefaultMaskStrategy()));
        field.setStatus(toStatus(request.getStatus()));
        if (field.getCreatedAt() == null && fieldMapper.selectById(field.getId()) == null) {
            fieldMapper.insert(field);
        } else {
            fieldMapper.updateById(field);
        }
    }

    private void upsertGuard(String tenantId,
                             String ownerService,
                             AuthorizationResourceSyncRequest.Guard request) {
        String guardCode = normalize(required(request.getGuardCode(), "AUTHZ_GUARD_CODE_REQUIRED"));
        SysAuthGuardTemplate guard = guardTemplateMapper.selectOne(new LambdaQueryWrapper<SysAuthGuardTemplate>()
                .eq(SysAuthGuardTemplate::getTenantId, tenantId)
                .eq(SysAuthGuardTemplate::getGuardCode, guardCode)
                .last("LIMIT 1"));
        if (guard == null) {
            guard = new SysAuthGuardTemplate();
            guard.setId(UlidGenerator.nextUlid());
            guard.setTenantId(tenantId);
            guard.setGuardCode(guardCode);
        }
        guard.setOwnerService(StringUtils.hasText(request.getOwnerService())
                ? request.getOwnerService().trim() : ownerService);
        guard.setSupportedResourceTypes(normalizeBlank(request.getSupportedResourceTypes()));
        guard.setConfigSchemaJson(normalizeBlank(request.getConfigSchemaJson()));
        guard.setDescription(normalizeBlank(request.getDescription()));
        guard.setStatus(toStatus(request.getStatus()));
        if (guard.getCreatedAt() == null && guardTemplateMapper.selectById(guard.getId()) == null) {
            guardTemplateMapper.insert(guard);
        } else {
            guardTemplateMapper.updateById(guard);
        }
    }

    private AuthorizationResourceTreeResponse.Group resourceGroup(
            String resourceType,
            List<AuthorizationResourceTreeResponse.ResourceNode> resources) {
        AuthorizationResourceTreeResponse.Group group = new AuthorizationResourceTreeResponse.Group();
        group.setResourceType(resourceType);
        group.setLabel(resourceTypeLabel(resourceType));
        group.setResources(resources);
        return group;
    }

    private AuthorizationResourceTreeResponse.ResourceNode resourceNode(
            SysAuthResource resource,
            List<SysAuthAction> actions,
            List<SysAuthField> fields,
            List<SysAuthGuardTemplate> guards) {
        AuthorizationResourceTreeResponse.ResourceNode node = new AuthorizationResourceTreeResponse.ResourceNode();
        node.setId(resource.getId());
        node.setResourceCode(resource.getResourceCode());
        node.setResourceType(resource.getResourceType());
        node.setOwnerService(resource.getOwnerService());
        node.setBusinessObjectId(resource.getBusinessObjectId());
        node.setDisplayName(resource.getDisplayName());
        node.setLifecycleStatus(resource.getLifecycleStatus());
        node.setLastSyncedAt(resource.getLastSyncedAt());
        node.setActions(actions.stream().map(this::actionNode).toList());
        node.setFields(fields.stream().map(this::fieldNode).toList());
        node.setGuards(guards.stream()
                .filter(guard -> guardSupportsResourceType(guard, resource.getResourceType()))
                .map(this::guardNode)
                .toList());
        return node;
    }

    private AuthorizationResourceTreeResponse.ActionNode actionNode(SysAuthAction action) {
        AuthorizationResourceTreeResponse.ActionNode node = new AuthorizationResourceTreeResponse.ActionNode();
        node.setActionCode(action.getActionCode());
        node.setActionCategory(action.getActionCategory());
        node.setDescription(action.getDescription());
        node.setGuardCodes(splitCodes(action.getGuardCodes()));
        node.setStatus(action.getStatus());
        return node;
    }

    private AuthorizationResourceTreeResponse.FieldNode fieldNode(SysAuthField field) {
        AuthorizationResourceTreeResponse.FieldNode node = new AuthorizationResourceTreeResponse.FieldNode();
        node.setFieldKey(field.getFieldKey());
        node.setFieldLabel(field.getFieldLabel());
        node.setFieldType(field.getFieldType());
        node.setSensitivityClassification(field.getSensitivityClassification());
        node.setDefaultMaskStrategy(field.getDefaultMaskStrategy());
        node.setStatus(field.getStatus());
        return node;
    }

    private AuthorizationResourceTreeResponse.GuardNode guardNode(SysAuthGuardTemplate guard) {
        AuthorizationResourceTreeResponse.GuardNode node = new AuthorizationResourceTreeResponse.GuardNode();
        node.setGuardCode(guard.getGuardCode());
        node.setOwnerService(guard.getOwnerService());
        node.setDescription(guard.getDescription());
        node.setStatus(guard.getStatus());
        return node;
    }

    private boolean guardSupportsResourceType(SysAuthGuardTemplate guard, String resourceType) {
        if (!StringUtils.hasText(guard.getSupportedResourceTypes())) {
            return true;
        }
        String normalizedResourceType = normalize(resourceType);
        return splitCodes(guard.getSupportedResourceTypes()).contains(normalizedResourceType);
    }

    private AuthorizationAdminOptionsResponse.Option option(
            String code,
            String label,
            String category,
            String description) {
        return AuthorizationAdminOptionsResponse.Option.of(code, label, category, description);
    }

    private String resourceTypeLabel(String resourceType) {
        return switch (normalize(resourceType)) {
            case "LOWCODE_APP" -> "低代码应用";
            case "LOWCODE_FORM" -> "低代码表单";
            case "WORKFLOW_TASK" -> "工作流任务";
            case "CUSTOM_DOC" -> "自定义单据";
            case "MENU" -> "菜单";
            case "API" -> "接口";
            default -> StringUtils.hasText(resourceType) ? resourceType : "其他";
        };
    }

    private void ensureActionRegistered(String tenantId, String resourceCode, String actionCode) {
        Long resourceCount = resourceMapper.selectCount(new LambdaQueryWrapper<SysAuthResource>()
                .eq(SysAuthResource::getTenantId, tenantId)
                .eq(SysAuthResource::getResourceCode, resourceCode)
                .eq(SysAuthResource::getLifecycleStatus, ACTIVE));
        Long count = actionMapper.selectCount(new LambdaQueryWrapper<SysAuthAction>()
                .eq(SysAuthAction::getTenantId, tenantId)
                .eq(SysAuthAction::getResourceCode, resourceCode)
                .eq(SysAuthAction::getActionCode, actionCode)
                .eq(SysAuthAction::getStatus, (short) 1));
        if (resourceCount == null || resourceCount == 0 || count == null || count == 0) {
            throw new BizException(40481, "AUTHZ_ACTION_NOT_REGISTERED");
        }
    }

    public String effectiveTenant(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId.trim() : DEFAULT_TENANT;
    }

    private String required(String value, String errorCode) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(40082, errorCode);
        }
        return value.trim();
    }

    private String normalizeEnum(String value, Set<String> allowed, String errorCode) {
        String normalized = normalize(required(value, errorCode));
        if (!allowed.contains(normalized)) {
            throw new BizException(40083, errorCode);
        }
        return normalized;
    }

    private String normalizeResource(String value) {
        return value != null ? value.trim() : null;
    }

    private String normalize(String value) {
        return value != null ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Short toStatus(Integer status) {
        return status != null && status == 0 ? (short) 0 : (short) 1;
    }

    public List<FieldPolicyResponse> listFieldPolicies(String tenantId,
                                                        String resourceCode,
                                                        String subjectType,
                                                        String subjectId) {
        LambdaQueryWrapper<SysAuthFieldPolicy> wrapper = new LambdaQueryWrapper<SysAuthFieldPolicy>()
                .eq(SysAuthFieldPolicy::getTenantId, effectiveTenant(tenantId))
                .eq(StringUtils.hasText(resourceCode), SysAuthFieldPolicy::getResourceCode, normalizeResource(resourceCode))
                .eq(StringUtils.hasText(subjectType), SysAuthFieldPolicy::getSubjectType, normalize(subjectType))
                .eq(StringUtils.hasText(subjectId), SysAuthFieldPolicy::getSubjectId, subjectId)
                .orderByAsc(SysAuthFieldPolicy::getResourceCode, SysAuthFieldPolicy::getFieldKey);
        return fieldPolicyMapper.selectList(wrapper).stream()
                .map(FieldPolicyResponse::from)
                .toList();
    }

    @Transactional
    public FieldPolicyResponse saveFieldPolicy(SaveFieldPolicyRequest request) {
        if (request == null) {
            throw new BizException(40081, "AUTHZ_FIELD_POLICY_REQUIRED");
        }
        String tenantId = effectiveTenant(request.getTenantId());
        String resourceCode = normalizeResource(required(request.getResourceCode(), "AUTHZ_RESOURCE_CODE_REQUIRED"));
        String fieldKey = required(request.getFieldKey(), "AUTHZ_FIELD_KEY_REQUIRED");
        String subjectType = normalize(request.getSubjectType());
        String subjectId = normalizeBlank(request.getSubjectId());

        SysAuthFieldPolicy policy = fieldPolicyMapper.selectOne(new LambdaQueryWrapper<SysAuthFieldPolicy>()
                .eq(SysAuthFieldPolicy::getTenantId, tenantId)
                .eq(SysAuthFieldPolicy::getResourceCode, resourceCode)
                .eq(SysAuthFieldPolicy::getFieldKey, fieldKey)
                .eq(SysAuthFieldPolicy::getSubjectType, subjectType)
                .eq(SysAuthFieldPolicy::getSubjectId, subjectId != null ? subjectId : "")
                .last("LIMIT 1"));
        if (policy == null) {
            policy = new SysAuthFieldPolicy();
            policy.setId(UlidGenerator.nextUlid());
            policy.setTenantId(tenantId);
            policy.setResourceCode(resourceCode);
            policy.setFieldKey(fieldKey);
            policy.setSubjectType(subjectType);
            policy.setSubjectId(subjectId);
        }
        policy.setReadMode(normalizeBlank(request.getReadMode()));
        policy.setWriteMode(normalizeBlank(request.getWriteMode()));
        policy.setMaskStrategy(normalizeBlank(request.getMaskStrategy()));
        policy.setEffect(normalizeBlank(request.getEffect()));
        policy.setStatus(toStatus(request.getStatus()));
        policy.setDescription(normalizeBlank(request.getDescription()));
        if (policy.getCreatedAt() == null && fieldPolicyMapper.selectById(policy.getId()) == null) {
            fieldPolicyMapper.insert(policy);
        } else {
            fieldPolicyMapper.updateById(policy);
        }
        versionService.bump(AuthorizationVersionService.FIELD_POLICY);
        versionService.bump(AuthorizationVersionService.AUTHORIZATION);
        return FieldPolicyResponse.from(policy);
    }

    @Transactional
    public void deleteFieldPolicy(String id) {
        if (!StringUtils.hasText(id) || fieldPolicyMapper.selectById(id) == null) {
            throw new BizException(40480, "AUTHZ_FIELD_POLICY_NOT_FOUND");
        }
        fieldPolicyMapper.deleteById(id);
        versionService.bump(AuthorizationVersionService.FIELD_POLICY);
        versionService.bump(AuthorizationVersionService.AUTHORIZATION);
    }

    public List<GuardTemplateResponse> listGuardTemplates(String tenantId, String ownerService) {
        return guardTemplateMapper.selectList(new LambdaQueryWrapper<SysAuthGuardTemplate>()
                        .eq(SysAuthGuardTemplate::getTenantId, effectiveTenant(tenantId))
                        .eq(StringUtils.hasText(ownerService), SysAuthGuardTemplate::getOwnerService, ownerService)
                        .orderByAsc(SysAuthGuardTemplate::getGuardCode))
                .stream()
                .map(GuardTemplateResponse::from)
                .toList();
    }

    @Transactional
    public GuardTemplateResponse saveGuardTemplate(SaveGuardTemplateRequest request) {
        if (request == null) {
            throw new BizException(40081, "AUTHZ_GUARD_TEMPLATE_REQUIRED");
        }
        String tenantId = effectiveTenant(request.getTenantId());
        String guardCode = normalize(required(request.getGuardCode(), "AUTHZ_GUARD_CODE_REQUIRED"));

        SysAuthGuardTemplate guard = guardTemplateMapper.selectOne(new LambdaQueryWrapper<SysAuthGuardTemplate>()
                .eq(SysAuthGuardTemplate::getTenantId, tenantId)
                .eq(SysAuthGuardTemplate::getGuardCode, guardCode)
                .last("LIMIT 1"));
        if (guard == null) {
            guard = new SysAuthGuardTemplate();
            guard.setId(UlidGenerator.nextUlid());
            guard.setTenantId(tenantId);
            guard.setGuardCode(guardCode);
        }
        guard.setOwnerService(StringUtils.hasText(request.getOwnerService())
                ? request.getOwnerService().trim() : DEFAULT_TENANT);
        guard.setSupportedResourceTypes(normalizeBlank(request.getSupportedResourceTypes()));
        guard.setConfigSchemaJson(normalizeBlank(request.getConfigSchemaJson()));
        guard.setDescription(normalizeBlank(request.getDescription()));
        guard.setStatus(toStatus(request.getStatus()));
        if (guard.getCreatedAt() == null && guardTemplateMapper.selectById(guard.getId()) == null) {
            guardTemplateMapper.insert(guard);
        } else {
            guardTemplateMapper.updateById(guard);
        }
        versionService.bump(AuthorizationVersionService.GUARD_TEMPLATE);
        versionService.bump(AuthorizationVersionService.AUTHORIZATION);
        return GuardTemplateResponse.from(guard);
    }

    @Transactional
    public void updateGuardTemplateStatus(String id, Integer status) {
        SysAuthGuardTemplate guard = guardTemplateMapper.selectById(id);
        if (guard == null) {
            throw new BizException(40480, "AUTHZ_GUARD_TEMPLATE_NOT_FOUND");
        }
        guard.setStatus(toStatus(status));
        guardTemplateMapper.updateById(guard);
        versionService.bump(AuthorizationVersionService.GUARD_TEMPLATE);
        versionService.bump(AuthorizationVersionService.AUTHORIZATION);
    }

    public PageResult<DecisionLogResponse> pageDecisionLogs(String tenantId,
                                                            String userId,
                                                            String resourceCode,
                                                            String actionCode,
                                                            LocalDateTime startTime,
                                                            LocalDateTime endTime,
                                                            int page,
                                                            int size) {
        LambdaQueryWrapper<SysAuthDecisionLog> wrapper = new LambdaQueryWrapper<SysAuthDecisionLog>()
                .eq(SysAuthDecisionLog::getTenantId, effectiveTenant(tenantId))
                .eq(StringUtils.hasText(userId), SysAuthDecisionLog::getUserId, userId)
                .eq(StringUtils.hasText(resourceCode), SysAuthDecisionLog::getResourceCode, normalizeResource(resourceCode))
                .eq(StringUtils.hasText(actionCode), SysAuthDecisionLog::getActionCode, normalize(actionCode))
                .ge(startTime != null, SysAuthDecisionLog::getDecidedAt, startTime)
                .le(endTime != null, SysAuthDecisionLog::getDecidedAt, endTime)
                .orderByDesc(SysAuthDecisionLog::getDecidedAt);
        IPage<SysAuthDecisionLog> result = decisionLogMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords().stream()
                .map(this::toDecisionLogResponse)
                .toList(), result.getTotal(), page, size);
    }

    public DecisionLogResponse getDecisionLog(String id) {
        SysAuthDecisionLog log = decisionLogMapper.selectById(id);
        if (log == null) {
            throw new BizException(40480, "AUTHZ_DECISION_LOG_NOT_FOUND");
        }
        return toDecisionLogResponse(log);
    }

    private DecisionLogResponse toDecisionLogResponse(SysAuthDecisionLog log) {
        DecisionLogResponse response = new DecisionLogResponse();
        response.setDecisionId(log.getId());
        response.setTenantId(log.getTenantId());
        response.setUserId(log.getUserId());
        response.setResourceCode(log.getResourceCode());
        response.setActionCode(log.getActionCode());
        response.setAllowed(log.getAllowed());
        response.setReasonCodes(log.getReasonCodes());
        response.setMatchedGrantId(log.getMatchedGrantId());
        response.setAuthVersion(log.getAuthVersion());
        response.setOwnerService(log.getOwnerService());
        response.setBusinessObjectId(log.getBusinessObjectId());
        response.setTraceId(log.getTraceId());
        response.setActionId(log.getActionId());
        response.setActionType(log.getActionType());
        response.setActionSource(log.getActionSource());
        response.setActionTargetType(log.getActionTargetType());
        response.setActionTargetId(log.getActionTargetId());
        response.setActionCorrelationId(log.getActionCorrelationId());
        response.setActionPayloadMetadata(log.getActionPayloadMetadata());
        response.setDecidedAt(log.getDecidedAt());
        return response;
    }

    private String joinCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        return codes.stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private List<String> splitCodes(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(this::normalize)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }
}
