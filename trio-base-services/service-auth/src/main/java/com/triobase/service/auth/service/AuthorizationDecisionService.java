package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.common.dto.authz.AuthorizationBatchDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationBatchDecisionResponse;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.common.dto.authz.AuthzDataScopeResult;
import com.triobase.common.dto.authz.AuthzBusinessActionCodes;
import com.triobase.common.dto.authz.AuthzDecisionReason;
import com.triobase.common.dto.authz.AuthzFieldRule;
import com.triobase.common.dto.authz.AuthzGuardRequirement;
import com.triobase.common.dto.authz.AuthzGuardResult;
import com.triobase.service.auth.dto.DataPolicyResponse;
import com.triobase.service.auth.dto.EffectiveDataPolicyResponse;
import com.triobase.service.auth.entity.SysAuthAction;
import com.triobase.service.auth.entity.SysAuthDecisionLog;
import com.triobase.service.auth.entity.SysAuthField;
import com.triobase.service.auth.entity.SysAuthFieldPolicy;
import com.triobase.service.auth.entity.SysAuthGrant;
import com.triobase.service.auth.entity.SysAuthGuardTemplate;
import com.triobase.service.auth.entity.SysAuthResource;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.entity.SysUser;
import com.triobase.service.auth.entity.SysUserRole;
import com.triobase.service.auth.mapper.AuthActionMapper;
import com.triobase.service.auth.mapper.AuthDecisionLogMapper;
import com.triobase.service.auth.mapper.AuthFieldMapper;
import com.triobase.service.auth.mapper.AuthFieldPolicyMapper;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.AuthGuardTemplateMapper;
import com.triobase.service.auth.mapper.AuthResourceMapper;
import com.triobase.service.auth.mapper.RoleMapper;
import com.triobase.service.auth.mapper.UserMapper;
import com.triobase.service.auth.mapper.UserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthorizationDecisionService {

    private static final String DEFAULT_TENANT = "default";
    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final String SUBJECT_ROLE = "ROLE";
    private static final String SUBJECT_USER = "USER";

    private final AuthResourceMapper resourceMapper;
    private final AuthActionMapper actionMapper;
    private final AuthGrantMapper grantMapper;
    private final AuthFieldMapper fieldMapper;
    private final AuthFieldPolicyMapper fieldPolicyMapper;
    private final AuthGuardTemplateMapper guardTemplateMapper;
    private final AuthDecisionLogMapper decisionLogMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final DataPolicyService dataPolicyService;
    private final AuthorizationVersionService versionService;
    private final ObjectMapper objectMapper;

    public AuthorizationDecisionResponse decide(AuthorizationDecisionRequest request) {
        if (request == null || !StringUtils.hasText(request.getResourceCode())
                || !StringUtils.hasText(request.getActionCode())) {
            throw new BizException(40084, "AUTHZ_DECISION_REQUIRED");
        }
        SubjectSnapshot subject = subject(request);
        String requestedResource = request.getResourceCode().trim();
        String actionCode = normalizeAction(request.getActionCode());

        AuthorizationDecisionResponse response = baseResponse(request, subject, requestedResource, actionCode);
        MatchedAction matchedAction = findRegisteredAction(subject.tenantId(), requestedResource, actionCode);
        if (matchedAction == null) {
            deny(response, "AUTHZ_RESOURCE_ACTION_NOT_REGISTERED",
                    "资源或动作未注册，按 fail-closed 拒绝", "RESOURCE", null);
            finalizeResponse(request, response, subject);
            return response;
        }
        response.setResourceCode(matchedAction.resourceCode());
        response.setOwnerService(matchedAction.resource() != null ? matchedAction.resource().getOwnerService() : null);
        response.setBusinessObjectId(matchedAction.resource() != null ? matchedAction.resource().getBusinessObjectId() : null);

        List<SysAuthGrant> grants = matchingGrants(subject, matchedAction.resourceCode(), actionCode);
        SysAuthGrant denyGrant = grants.stream()
                .filter(grant -> "DENY".equalsIgnoreCase(grant.getEffect()))
                .findFirst()
                .orElse(null);
        SysAuthGrant allowGrant = grants.stream()
                .filter(grant -> "ALLOW".equalsIgnoreCase(grant.getEffect()))
                .findFirst()
                .orElse(null);

        if (denyGrant != null) {
            response.setMatchedGrantId(denyGrant.getId());
            response.setEffect("DENY");
            deny(response, "AUTHZ_DENY_GRANT_MATCHED",
                    "匹配到拒绝授权，拒绝优先", "GRANT", denyGrant.getId());
        } else if (allowGrant != null) {
            response.setAllowed(true);
            response.setMatchedGrantId(allowGrant.getId());
            response.setEffect("ALLOW");
            response.getReasons().add(AuthzDecisionReason.of("AUTHZ_ALLOW_GRANT_MATCHED",
                    "匹配到允许授权", "GRANT", allowGrant.getId()));
        } else if (subject.admin()) {
            response.setAllowed(true);
            response.setEffect("ALLOW");
            response.getReasons().add(AuthzDecisionReason.of("AUTHZ_ADMIN_REGISTERED_RESOURCE",
                    "超级管理员允许访问已注册资源", "ROLE", subject.adminRoleId()));
        } else {
            deny(response, "AUTHZ_GRANT_NOT_FOUND",
                    "没有匹配的允许授权", "GRANT", null);
        }

        response.setDataScope(dataScope(subject, matchedAction.resourceCode(), actionCode));
        response.setFieldRules(fieldRules(subject, matchedAction.resourceCode(), request.getFieldKeys(), response.isAllowed()));
        response.setGuardRequirements(guardRequirements(subject.tenantId(), matchedAction.action()));
        response.setGuardResults(request.getGuardResults() != null ? request.getGuardResults() : List.of());
        applyGuardResults(response);
        applyAvailabilityRendering(response);
        finalizeResponse(request, response, subject);
        return response;
    }

    public AuthorizationBatchDecisionResponse batchDecide(AuthorizationBatchDecisionRequest request) {
        AuthorizationBatchDecisionResponse response = new AuthorizationBatchDecisionResponse();
        if (request == null || request.getDecisions() == null || request.getDecisions().isEmpty()) {
            return response;
        }
        response.setDecisions(request.getDecisions().stream().map(this::decide).toList());
        return response;
    }

    private AuthorizationDecisionResponse baseResponse(AuthorizationDecisionRequest request,
                                                       SubjectSnapshot subject,
                                                       String resourceCode,
                                                       String actionCode) {
        AuthorizationDecisionResponse response = new AuthorizationDecisionResponse();
        response.setDecisionId(UlidGenerator.nextUlid());
        response.setTenantId(subject.tenantId());
        response.setUserId(subject.userId());
        response.setResourceCode(resourceCode);
        response.setActionCode(actionCode);
        response.setOwnerService(request.getOwnerService());
        response.setBusinessObjectId(request.getBusinessObjectId());
        response.setAuthorizationVersion(versionService.current(AuthorizationVersionService.AUTHORIZATION));
        response.setRoleVersion(versionService.current(AuthorizationVersionService.GRANT));
        response.setDataPolicyVersion(versionService.current(AuthorizationVersionService.DATA_POLICY));
        response.setFieldPolicyVersion(versionService.current(AuthorizationVersionService.FIELD_POLICY));
        response.setGuardTemplateVersion(versionService.current(AuthorizationVersionService.GUARD_TEMPLATE));
        response.getRenderingMetadata().put("businessDocumentAction",
                AuthzBusinessActionCodes.isDocumentAction(actionCode));
        response.getRenderingMetadata().put("semanticActionCode",
                AuthzBusinessActionCodes.normalize(actionCode));
        return response;
    }

    private SubjectSnapshot subject(AuthorizationDecisionRequest request) {
        String userId = StringUtils.hasText(request.getUserId())
                ? request.getUserId().trim() : SecurityContextHolder.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new BizException(40085, "AUTHZ_USER_REQUIRED");
        }
        SysUser user = userMapper.selectById(userId);
        String tenantId = StringUtils.hasText(request.getTenantId())
                ? request.getTenantId().trim()
                : user != null && StringUtils.hasText(user.getTenantId())
                ? user.getTenantId()
                : StringUtils.hasText(SecurityContextHolder.getTenantId())
                ? SecurityContextHolder.getTenantId()
                : DEFAULT_TENANT;
        List<String> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream()
                .map(SysUserRole::getRoleId)
                .toList();
        List<SysRole> roles = roleIds.isEmpty()
                ? List.of()
                : roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getStatus, (short) 1));
        String adminRoleId = roles.stream()
                .filter(role -> ADMIN_ROLE_CODE.equals(role.getRoleCode()))
                .map(SysRole::getId)
                .findFirst()
                .orElse(null);
        return new SubjectSnapshot(userId, tenantId, roleIds, roles, StringUtils.hasText(adminRoleId), adminRoleId);
    }

    private MatchedAction findRegisteredAction(String tenantId, String requestedResource, String actionCode) {
        List<SysAuthAction> actions = actionMapper.selectList(new LambdaQueryWrapper<SysAuthAction>()
                .eq(SysAuthAction::getTenantId, tenantId)
                .eq(SysAuthAction::getActionCode, actionCode)
                .eq(SysAuthAction::getStatus, (short) 1));
        for (SysAuthAction action : actions) {
            if (codeMatches(action.getResourceCode(), requestedResource)) {
                SysAuthResource resource = resourceMapper.selectOne(new LambdaQueryWrapper<SysAuthResource>()
                        .eq(SysAuthResource::getTenantId, tenantId)
                        .eq(SysAuthResource::getResourceCode, action.getResourceCode())
                        .eq(SysAuthResource::getLifecycleStatus, "ACTIVE")
                        .last("LIMIT 1"));
                if (resource != null) {
                    return new MatchedAction(action.getResourceCode(), action, resource);
                }
            }
        }
        return null;
    }

    private List<SysAuthGrant> matchingGrants(SubjectSnapshot subject, String resourceCode, String actionCode) {
        List<SysAuthGrant> candidates = grantMapper.selectList(new LambdaQueryWrapper<SysAuthGrant>()
                .eq(SysAuthGrant::getTenantId, subject.tenantId())
                .eq(SysAuthGrant::getStatus, (short) 1));
        return candidates.stream()
                .filter(grant -> subjectMatches(subject, grant.getSubjectType(), grant.getSubjectId()))
                .filter(grant -> actionMatches(grant.getActionCode(), actionCode))
                .filter(grant -> codeMatches(grant.getResourceCode(), resourceCode))
                .toList();
    }

    private AuthzDataScopeResult dataScope(SubjectSnapshot subject, String resourceCode, String actionCode) {
        AuthzDataScopeResult result = new AuthzDataScopeResult();
        try {
            EffectiveDataPolicyResponse effective = dataPolicyService.resolveEffective(
                    subject.tenantId(), subject.userId(), resourceCode, actionCode);
            if (effective == null) {
                return result;
            }
            result.setRestrictive(effective.isRestrictive());
            result.setOrgContextResolved(effective.isOrgContextResolved());
            result.setRoleIds(effective.getRoleIds() != null ? effective.getRoleIds() : List.of());
            List<String> scopeTypes = new ArrayList<>();
            List<String> orgUnitIds = new ArrayList<>();
            List<String> policyIds = new ArrayList<>();
            if (effective.getPolicies() != null) {
                for (DataPolicyResponse policy : effective.getPolicies()) {
                    policyIds.add(policy.getId());
                    if (policy.getDimensions() == null) {
                        continue;
                    }
                    policy.getDimensions().forEach(dimension -> {
                        if (StringUtils.hasText(dimension.getScopeType())) {
                            scopeTypes.add(dimension.getScopeType());
                        }
                        if (dimension.getOrgUnitIds() != null) {
                            orgUnitIds.addAll(dimension.getOrgUnitIds());
                        }
                    });
                }
            }
            result.setScopeTypes(scopeTypes.stream().filter(StringUtils::hasText).distinct().toList());
            result.setOrgUnitIds(orgUnitIds.stream().filter(StringUtils::hasText).distinct().toList());
            result.setPolicyIds(policyIds.stream().filter(StringUtils::hasText).distinct().toList());
            return result;
        } catch (RuntimeException ignored) {
            return result;
        }
    }

    private List<AuthzFieldRule> fieldRules(SubjectSnapshot subject,
                                            String resourceCode,
                                            List<String> requestedFieldKeys,
                                            boolean functionAllowed) {
        List<String> fieldKeys = requestedFieldKeys != null && !requestedFieldKeys.isEmpty()
                ? requestedFieldKeys.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList()
                : fieldMapper.selectList(new LambdaQueryWrapper<SysAuthField>()
                        .eq(SysAuthField::getTenantId, subject.tenantId())
                        .eq(SysAuthField::getResourceCode, resourceCode)
                        .eq(SysAuthField::getStatus, (short) 1)
                        .orderByAsc(SysAuthField::getFieldKey))
                .stream()
                .map(SysAuthField::getFieldKey)
                .toList();
        if (fieldKeys.isEmpty()) {
            return List.of();
        }
        List<SysAuthFieldPolicy> policies = fieldPolicyMapper.selectList(new LambdaQueryWrapper<SysAuthFieldPolicy>()
                .eq(SysAuthFieldPolicy::getTenantId, subject.tenantId())
                .eq(SysAuthFieldPolicy::getResourceCode, resourceCode)
                .eq(SysAuthFieldPolicy::getStatus, (short) 1)
                .in(SysAuthFieldPolicy::getFieldKey, fieldKeys));
        List<AuthzFieldRule> rules = new ArrayList<>();
        for (String fieldKey : fieldKeys) {
            AuthzFieldRule rule = defaultFieldRule(fieldKey, functionAllowed);
            List<SysAuthFieldPolicy> matching = policies.stream()
                    .filter(policy -> fieldKey.equals(policy.getFieldKey()))
                    .filter(policy -> subjectMatches(subject, policy.getSubjectType(), policy.getSubjectId()))
                    .toList();
            SysAuthFieldPolicy deny = matching.stream()
                    .filter(policy -> "DENY".equalsIgnoreCase(policy.getEffect()))
                    .findFirst()
                    .orElse(null);
            SysAuthFieldPolicy allow = matching.stream()
                    .filter(policy -> "ALLOW".equalsIgnoreCase(policy.getEffect()))
                    .findFirst()
                    .orElse(null);
            if (deny != null) {
                rule.setReadMode("HIDDEN");
                rule.setWriteMode("DENIED");
                rule.setMatchedPolicyId(deny.getId());
                rule.setReasonCode("AUTHZ_FIELD_DENY_POLICY");
                rule.setReasonMessage("匹配到字段拒绝策略");
            } else if (allow != null) {
                rule.setReadMode(normalizeFieldMode(allow.getReadMode(), "VISIBLE"));
                rule.setWriteMode(normalizeFieldMode(allow.getWriteMode(), "EDITABLE"));
                rule.setMaskStrategy(allow.getMaskStrategy());
                rule.setMatchedPolicyId(allow.getId());
                rule.setReasonCode("AUTHZ_FIELD_ALLOW_POLICY");
                rule.setReasonMessage("匹配到字段允许策略");
            }
            rules.add(rule);
        }
        return rules;
    }

    private AuthzFieldRule defaultFieldRule(String fieldKey, boolean functionAllowed) {
        AuthzFieldRule rule = new AuthzFieldRule();
        rule.setFieldKey(fieldKey);
        rule.setReadMode(functionAllowed ? "VISIBLE" : "HIDDEN");
        rule.setWriteMode(functionAllowed ? "EDITABLE" : "DENIED");
        rule.setReasonCode(functionAllowed ? "AUTHZ_FIELD_DEFAULT_VISIBLE" : "AUTHZ_FUNCTION_DENIED");
        rule.setReasonMessage(functionAllowed ? "默认字段可见可编辑" : "功能权限被拒绝，字段不可访问");
        return rule;
    }

    private List<AuthzGuardRequirement> guardRequirements(String tenantId, SysAuthAction action) {
        List<String> codes = splitCodes(action.getGuardCodes());
        if (codes.isEmpty()) {
            return List.of();
        }
        return guardTemplateMapper.selectList(new LambdaQueryWrapper<SysAuthGuardTemplate>()
                        .eq(SysAuthGuardTemplate::getTenantId, tenantId)
                        .in(SysAuthGuardTemplate::getGuardCode, codes)
                        .eq(SysAuthGuardTemplate::getStatus, (short) 1))
                .stream()
                .map(this::toGuardRequirement)
                .toList();
    }

    private void applyGuardResults(AuthorizationDecisionResponse response) {
        if (response.getGuardResults() == null || response.getGuardResults().isEmpty()) {
            return;
        }
        for (AuthzGuardResult guardResult : response.getGuardResults()) {
            if (!guardResult.isAllowed()) {
                response.setAllowed(false);
                response.getReasons().add(AuthzDecisionReason.of(
                        StringUtils.hasText(guardResult.getReasonCode())
                                ? guardResult.getReasonCode() : "AUTHZ_GUARD_DENIED",
                        StringUtils.hasText(guardResult.getReasonMessage())
                                ? guardResult.getReasonMessage() : "领域守卫拒绝当前动作",
                        "GUARD",
                        guardResult.getGuardCode()));
            }
        }
    }

    private void applyAvailabilityRendering(AuthorizationDecisionResponse response) {
        response.setEnabled(response.isAllowed());
        if (response.isAllowed()) {
            response.setVisible(true);
            response.setDisabledReason(null);
            response.setDisplayReason(null);
            return;
        }
        String reasonCode = response.getReasons().stream()
                .map(AuthzDecisionReason::getCode)
                .filter(StringUtils::hasText)
                .filter(code -> !code.toUpperCase(Locale.ROOT).contains("ALLOW"))
                .findFirst()
                .orElse("AUTHZ_DENIED");
        response.setDisabledReason(reasonCode);
        response.setDisplayReason(response.getReasons().stream()
                .filter(reason -> StringUtils.hasText(reason.getCode())
                        && !reason.getCode().toUpperCase(Locale.ROOT).contains("ALLOW"))
                .map(AuthzDecisionReason::getMessage)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(reasonCode));
        boolean hidden = response.getReasons().stream()
                .map(AuthzDecisionReason::getCode)
                .filter(StringUtils::hasText)
                .anyMatch(this::hiddenReason);
        response.setVisible(!hidden);
    }

    private void finalizeResponse(AuthorizationDecisionRequest request,
                                  AuthorizationDecisionResponse response,
                                  SubjectSnapshot subject) {
        if (request.enforcementMode()) {
            audit(request, response, subject);
        }
    }

    private void audit(AuthorizationDecisionRequest request,
                       AuthorizationDecisionResponse response,
                       SubjectSnapshot subject) {
        SysAuthDecisionLog log = new SysAuthDecisionLog();
        log.setId(response.getDecisionId());
        log.setTenantId(response.getTenantId());
        log.setUserId(response.getUserId());
        log.setSubjectSnapshot(toJson(subject.roles().stream().map(SysRole::getRoleCode).toList()));
        log.setResourceCode(response.getResourceCode());
        log.setActionCode(response.getActionCode());
        log.setAllowed(response.isAllowed() ? (short) 1 : (short) 0);
        log.setReasonCodes(response.getReasons().stream()
                .map(AuthzDecisionReason::getCode)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.joining(",")));
        log.setMatchedGrantId(response.getMatchedGrantId());
        log.setDataScopeSnapshot(toJson(response.getDataScope()));
        log.setFieldRuleSnapshot(toJson(response.getFieldRules()));
        log.setGuardSnapshot(toJson(response.getGuardRequirements()));
        log.setAuthVersion(response.getAuthorizationVersion());
        log.setRoleVersion(response.getRoleVersion());
        log.setDataPolicyVersion(response.getDataPolicyVersion());
        log.setFieldPolicyVersion(response.getFieldPolicyVersion());
        log.setGuardTemplateVersion(response.getGuardTemplateVersion());
        log.setOwnerService(response.getOwnerService());
        log.setBusinessObjectId(response.getBusinessObjectId());
        log.setTraceId(StringUtils.hasText(TraceUtil.getTraceId()) ? TraceUtil.getTraceId()
                : firstNonBlank(attr(request, "traceId"), request.getActionCorrelationId()));
        log.setActionId(request.getActionId());
        log.setActionType(firstNonBlank(request.getActionType(), attr(request, "actionType")));
        log.setActionSource(firstNonBlank(request.getActionSource(), attr(request, "source")));
        log.setActionTargetType(firstNonBlank(request.getActionTargetType(), attr(request, "targetType")));
        log.setActionTargetId(firstNonBlank(request.getActionTargetId(), attr(request, "targetId")));
        log.setActionCorrelationId(firstNonBlank(request.getActionCorrelationId(), attr(request, "correlationId")));
        log.setActionPayloadMetadata(toJson(request.getActionPayloadMetadata()));
        log.setDecidedAt(LocalDateTime.now());
        decisionLogMapper.insert(log);
    }

    private String attr(AuthorizationDecisionRequest request, String key) {
        if (request.getAttributes() == null || !request.getAttributes().containsKey(key)) {
            return null;
        }
        Object value = request.getAttributes().get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private AuthzGuardRequirement toGuardRequirement(SysAuthGuardTemplate guard) {
        AuthzGuardRequirement requirement = new AuthzGuardRequirement();
        requirement.setGuardCode(guard.getGuardCode());
        requirement.setOwnerService(guard.getOwnerService());
        requirement.setDescription(guard.getDescription());
        requirement.setConfigSchemaJson(guard.getConfigSchemaJson());
        return requirement;
    }

    private boolean subjectMatches(SubjectSnapshot subject, String subjectType, String subjectId) {
        if (SUBJECT_USER.equalsIgnoreCase(subjectType)) {
            return subject.userId().equals(subjectId);
        }
        return SUBJECT_ROLE.equalsIgnoreCase(subjectType) && subject.roleIds().contains(subjectId);
    }

    private void deny(AuthorizationDecisionResponse response,
                      String code,
                      String message,
                      String source,
                      String evidenceId) {
        response.setAllowed(false);
        response.setEnabled(false);
        response.setDisabledReason(code);
        response.setDisplayReason(message);
        response.setVisible(!hiddenReason(code));
        response.getReasons().add(AuthzDecisionReason.of(code, message, source, evidenceId));
    }

    private boolean hiddenReason(String code) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        String normalized = code.toUpperCase(Locale.ROOT);
        return normalized.contains("HIDDEN")
                || normalized.contains("NOT_APPLICABLE")
                || normalized.contains("NOT_REGISTERED");
    }

    private String normalizeAction(String actionCode) {
        return actionCode.trim().toUpperCase(Locale.ROOT);
    }

    private boolean actionMatches(String grantedAction, String requiredAction) {
        return "*".equals(grantedAction) || requiredAction.equalsIgnoreCase(grantedAction);
    }

    private boolean codeMatches(String granted, String required) {
        if (!StringUtils.hasText(granted) || !StringUtils.hasText(required)) {
            return false;
        }
        if (granted.equals(required)) {
            return true;
        }
        if (!granted.contains("*")) {
            return false;
        }
        String regex = Pattern.quote(granted).replace("*", "\\E.*\\Q");
        return Pattern.compile(regex).matcher(required).matches();
    }

    private String normalizeFieldMode(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : defaultValue;
    }

    private List<String> splitCodes(String codes) {
        if (!StringUtils.hasText(codes)) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String code : codes.split(",")) {
            if (StringUtils.hasText(code)) {
                result.add(code.trim().toUpperCase(Locale.ROOT));
            }
        }
        return result.stream().toList();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private record MatchedAction(String resourceCode, SysAuthAction action, SysAuthResource resource) {
    }

    private record SubjectSnapshot(String userId,
                                   String tenantId,
                                   List<String> roleIds,
                                   List<SysRole> roles,
                                   boolean admin,
                                   String adminRoleId) {
    }
}
