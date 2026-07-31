package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.auth.dto.AuthorizationBundleRequest;
import com.triobase.service.auth.dto.AuthorizationBundleResponse;
import com.triobase.service.auth.entity.SysAuthAction;
import com.triobase.service.auth.entity.SysAuthBundleReceipt;
import com.triobase.service.auth.entity.SysAuthGrant;
import com.triobase.service.auth.entity.SysAuthResource;
import com.triobase.service.auth.mapper.AuthActionMapper;
import com.triobase.service.auth.mapper.AuthGrantMapper;
import com.triobase.service.auth.mapper.AuthResourceMapper;
import com.triobase.service.auth.mapper.SysAuthBundleReceiptMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LowcodeAuthorizationBundleService {

    private static final Map<String, List<String>> APP_ACTIONS = Map.of(
            "APPLICANT", List.of("VIEW"), "APPROVER", List.of("VIEW"),
            "DESIGNER", List.of("VIEW", "DESIGN"),
            "ADMIN", List.of("VIEW", "DESIGN", "PUBLISH", "OFFLINE"));
    private static final Map<String, List<String>> FORM_ACTIONS = Map.of(
            "APPLICANT", List.of("VIEW", "CREATE", "EDIT", "SUBMIT", "FIELD_READ", "FIELD_WRITE"),
            "APPROVER", List.of("VIEW", "APPROVE", "REJECT", "FIELD_READ", "FIELD_WRITE"),
            "DESIGNER", List.of("VIEW", "DESIGN", "FIELD_READ"),
            "ADMIN", List.of("VIEW", "CREATE", "EDIT", "DELETE", "SUBMIT", "APPROVE", "REJECT",
                    "EXPORT", "DESIGN", "PUBLISH", "OFFLINE", "FIELD_READ", "FIELD_WRITE"));

    private final AuthResourceMapper resourceMapper;
    private final AuthActionMapper actionMapper;
    private final AuthGrantMapper grantMapper;
    private final SysAuthBundleReceiptMapper receiptMapper;
    private final AuthorizationVersionService versionService;
    private final AuthorizationRegistryService registryService;
    private final ObjectMapper objectMapper;

    public AuthorizationBundleResponse preview(AuthorizationBundleRequest request) {
        return compile(request);
    }

    @Transactional
    public AuthorizationBundleResponse apply(AuthorizationBundleRequest request) {
        AuthorizationBundleResponse response = compile(request);
        String key = required(request.getIdempotencyKey(), "AUTHZ_BUNDLE_IDEMPOTENCY_KEY_REQUIRED");
        String requestHash = hash(response.getTenantId() + "|" + response.getRoleId() + "|"
                + response.getApplicationResourceCode() + "|" + response.getPreset());
        SysAuthBundleReceipt receipt = receiptMapper.selectOne(new LambdaQueryWrapper<SysAuthBundleReceipt>()
                .eq(SysAuthBundleReceipt::getTenantId, response.getTenantId())
                .eq(SysAuthBundleReceipt::getIdempotencyKey, key).last("LIMIT 1"));
        if (receipt != null) {
            if (!requestHash.equals(receipt.getRequestHash())) {
                throw new BizException(40984, "AUTHZ_BUNDLE_IDEMPOTENCY_CONFLICT");
            }
            response.setApplied(true);
            response.setReplayed(true);
            response.setAuthorizationVersion(receipt.getAuthorizationVersion());
            return response;
        }
        int inserted = 0;
        for (AuthorizationBundleResponse.GrantChange change : response.getChanges()) {
            if (!"ADD".equals(change.getState())) continue;
            SysAuthGrant grant = new SysAuthGrant();
            grant.setId(UlidGenerator.nextUlid());
            grant.setTenantId(response.getTenantId());
            grant.setSubjectType("ROLE");
            grant.setSubjectId(response.getRoleId());
            grant.setResourceCode(change.getResourceCode());
            grant.setActionCode(change.getActionCode());
            grant.setEffect("ALLOW");
            grant.setStatus((short) 1);
            grant.setDescription("Lowcode bundle " + response.getPreset());
            grantMapper.insert(grant);
            inserted++;
        }
        long version = inserted == 0 ? versionService.current(AuthorizationVersionService.AUTHORIZATION)
                : versionService.bump(AuthorizationVersionService.AUTHORIZATION);
        if (inserted > 0) versionService.bump(AuthorizationVersionService.GRANT);
        receipt = new SysAuthBundleReceipt();
        receipt.setId(UlidGenerator.nextUlid());
        receipt.setTenantId(response.getTenantId());
        receipt.setIdempotencyKey(key);
        receipt.setRoleId(response.getRoleId());
        receipt.setApplicationResourceCode(response.getApplicationResourceCode());
        receipt.setPreset(response.getPreset());
        receipt.setRequestHash(requestHash);
        receipt.setGrantCount(inserted);
        receipt.setAuthorizationVersion(version);
        receiptMapper.insert(receipt);
        response.setApplied(true);
        response.setAuthorizationVersion(version);
        return response;
    }

    private AuthorizationBundleResponse compile(AuthorizationBundleRequest request) {
        if (request == null) throw new BizException(40084, "AUTHZ_BUNDLE_REQUIRED");
        String tenant = registryService.effectiveTenant(request.getTenantId());
        String roleId = required(request.getRoleId(), "AUTHZ_BUNDLE_ROLE_REQUIRED");
        String appCode = required(request.getApplicationResourceCode(), "AUTHZ_BUNDLE_APPLICATION_REQUIRED")
                .trim().toUpperCase(Locale.ROOT);
        String preset = required(request.getPreset(), "AUTHZ_BUNDLE_PRESET_REQUIRED").trim().toUpperCase(Locale.ROOT);
        if (!APP_ACTIONS.containsKey(preset)) throw new BizException(40085, "AUTHZ_BUNDLE_PRESET_INVALID");
        SysAuthResource app = activeResource(tenant, appCode, "LOWCODE_APP");
        String formKey;
        try {
            JsonNode metadata = objectMapper.readTree(app.getMetadataJson());
            if (metadata.path("authorizationBlueprintVersion").asInt() != 1) {
                throw new BizException(40985, "AUTHZ_BUNDLE_BLUEPRINT_UNSUPPORTED");
            }
            formKey = metadata.path("formKey").asText();
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(40986, "AUTHZ_BUNDLE_BLUEPRINT_INVALID");
        }
        String formCode = "LOWCODE_FORM:" + required(formKey, "AUTHZ_BUNDLE_FORM_REQUIRED").toUpperCase(Locale.ROOT);
        activeResource(tenant, formCode, "LOWCODE_FORM");
        LinkedHashMap<String, List<String>> plan = new LinkedHashMap<>();
        plan.put(appCode, APP_ACTIONS.get(preset));
        plan.put(formCode, FORM_ACTIONS.get(preset));
        validateActions(tenant, plan);
        Set<String> existing = grantMapper.selectList(new LambdaQueryWrapper<SysAuthGrant>()
                        .eq(SysAuthGrant::getTenantId, tenant).eq(SysAuthGrant::getSubjectType, "ROLE")
                        .eq(SysAuthGrant::getSubjectId, roleId).eq(SysAuthGrant::getEffect, "ALLOW")
                        .eq(SysAuthGrant::getStatus, (short) 1).in(SysAuthGrant::getResourceCode, plan.keySet()))
                .stream().map(g -> g.getResourceCode() + "|" + g.getActionCode())
                .collect(java.util.stream.Collectors.toSet());
        List<AuthorizationBundleResponse.GrantChange> changes = new ArrayList<>();
        plan.forEach((resource, actions) -> actions.forEach(action -> {
            AuthorizationBundleResponse.GrantChange change = new AuthorizationBundleResponse.GrantChange();
            change.setResourceCode(resource);
            change.setActionCode(action);
            change.setState(existing.contains(resource + "|" + action) ? "UNCHANGED" : "ADD");
            changes.add(change);
        }));
        AuthorizationBundleResponse response = new AuthorizationBundleResponse();
        response.setTenantId(tenant); response.setRoleId(roleId);
        response.setApplicationResourceCode(appCode); response.setFormResourceCode(formCode);
        response.setPreset(preset); response.setApplicable(true); response.setChanges(changes);
        response.setAuthorizationVersion(versionService.current(AuthorizationVersionService.AUTHORIZATION));
        return response;
    }

    private SysAuthResource activeResource(String tenant, String code, String type) {
        SysAuthResource resource = resourceMapper.selectOne(new LambdaQueryWrapper<SysAuthResource>()
                .eq(SysAuthResource::getTenantId, tenant).eq(SysAuthResource::getResourceCode, code)
                .eq(SysAuthResource::getResourceType, type).eq(SysAuthResource::getLifecycleStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (resource == null) throw new BizException(40987, "AUTHZ_BUNDLE_RESOURCE_NOT_ACTIVE");
        return resource;
    }

    private void validateActions(String tenant, Map<String, List<String>> plan) {
        for (Map.Entry<String, List<String>> entry : plan.entrySet()) {
            long count = actionMapper.selectCount(new LambdaQueryWrapper<SysAuthAction>()
                    .eq(SysAuthAction::getTenantId, tenant).eq(SysAuthAction::getResourceCode, entry.getKey())
                    .eq(SysAuthAction::getStatus, (short) 1).in(SysAuthAction::getActionCode, entry.getValue()));
            if (count != entry.getValue().size()) throw new BizException(40988, "AUTHZ_BUNDLE_ACTION_DRIFT");
        }
    }

    private String required(String value, String error) {
        if (!StringUtils.hasText(value)) throw new BizException(40086, error);
        return value.trim();
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
