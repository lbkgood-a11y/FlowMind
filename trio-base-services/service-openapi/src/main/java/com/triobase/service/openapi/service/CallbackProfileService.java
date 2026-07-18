package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.ApplicationClient;
import com.triobase.service.openapi.domain.entity.CallbackProfile;
import com.triobase.service.openapi.domain.entity.CallbackProfileVersion;
import com.triobase.service.openapi.domain.entity.MappingVersion;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.ApplicationLifecycleState;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.VersionLifecycleState;
import com.triobase.service.openapi.dto.CallbackProfileVersionMutationRequest;
import com.triobase.service.openapi.dto.CallbackProfileVersionResponse;
import com.triobase.service.openapi.dto.CreateCallbackProfileRequest;
import com.triobase.service.openapi.infrastructure.mapper.ApplicationClientMapper;
import com.triobase.service.openapi.infrastructure.mapper.CallbackProfileMapper;
import com.triobase.service.openapi.infrastructure.mapper.CallbackProfileVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.MappingVersionMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CallbackProfileService {

    private static final Set<AuthenticationType> CALLBACK_AUTH = Set.of(
            AuthenticationType.HMAC, AuthenticationType.RSA);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CallbackProfileMapper profileMapper;
    private final CallbackProfileVersionMapper versionMapper;
    private final ApplicationClientMapper clientMapper;
    private final StructureVersionMapper structureMapper;
    private final MappingVersionMapper mappingMapper;
    private final CredentialProvider credentialProvider;
    private final IntegrationAuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public CallbackProfileVersionResponse create(CreateCallbackProfileRequest request) {
        String tenantId = targetTenant(request.tenantId());
        CallbackProfile profile = new CallbackProfile();
        LocalDateTime now = LocalDateTime.now();
        profile.setId(UlidGenerator.nextUlid());
        profile.setTenantId(tenantId);
        profile.setCallbackKey(generateCallbackKey());
        profile.setDisplayName(request.displayName().trim());
        profile.setOwnerId(request.ownerId().trim());
        profile.setLifecycleState(AssetLifecycleState.ACTIVE);
        initialize(profile, now);
        profileMapper.insert(profile);

        CallbackProfileVersion version = new CallbackProfileVersion();
        version.setId(UlidGenerator.nextUlid());
        version.setCallbackProfileId(profile.getId());
        version.setVersionNumber(1);
        version.setLifecycleState(VersionLifecycleState.DRAFT);
        apply(version, request.version());
        version.setValidationResult(validate(profile, version));
        initialize(version, now);
        versionMapper.insert(version);
        auditService.success("CALLBACK_PROFILE_CREATED", "CALLBACK_PROFILE", profile.getId(),
                version.getValidationResult());
        return response(profile, version);
    }

    @Transactional
    public CallbackProfileVersionResponse createDraft(
            String profileId, CallbackProfileVersionMutationRequest request) {
        CallbackProfile profile = requireProfile(profileId);
        if (versionMapper.selectCount(new LambdaQueryWrapper<CallbackProfileVersion>()
                .eq(CallbackProfileVersion::getCallbackProfileId, profileId)
                .eq(CallbackProfileVersion::getLifecycleState, VersionLifecycleState.DRAFT)) > 0) {
            throw new BizException(40970, "OPENAPI_CALLBACK_DRAFT_ALREADY_EXISTS");
        }
        CallbackProfileVersion latest = versionMapper.selectOne(
                new LambdaQueryWrapper<CallbackProfileVersion>()
                        .eq(CallbackProfileVersion::getCallbackProfileId, profileId)
                        .orderByDesc(CallbackProfileVersion::getVersionNumber).last("LIMIT 1"));
        CallbackProfileVersion version = new CallbackProfileVersion();
        version.setId(UlidGenerator.nextUlid());
        version.setCallbackProfileId(profileId);
        version.setVersionNumber(latest == null ? 1 : latest.getVersionNumber() + 1);
        version.setLifecycleState(VersionLifecycleState.DRAFT);
        apply(version, request);
        version.setValidationResult(validate(profile, version));
        initialize(version, LocalDateTime.now());
        versionMapper.insert(version);
        return response(profile, version);
    }

    @Transactional
    public CallbackProfileVersionResponse updateDraft(
            String versionId, CallbackProfileVersionMutationRequest request) {
        CallbackProfileVersion version = requireVersion(versionId);
        CallbackProfile profile = requireProfile(version.getCallbackProfileId());
        requireDraft(version);
        apply(version, request);
        version.setValidationResult(validate(profile, version));
        touch(version);
        versionMapper.updateById(version);
        return response(profile, version);
    }

    @Transactional
    public CallbackProfileVersionResponse publish(String versionId) {
        CallbackProfileVersion version = requireVersion(versionId);
        CallbackProfile profile = requireProfile(version.getCallbackProfileId());
        requireDraft(version);
        ObjectNode validation = validate(profile, version);
        if (!validation.path("valid").asBoolean()) {
            throw new BizException(42270,
                    "OPENAPI_CALLBACK_PROFILE_INVALID:" + validation.path("errors"));
        }
        credentialProvider.resolve(version.getSecretReference());
        version.setValidationResult(validation);
        version.setLifecycleState(VersionLifecycleState.PUBLISHED);
        version.setPublishedBy(operator());
        version.setPublishedAt(LocalDateTime.now());
        touch(version);
        versionMapper.updateById(version);
        auditService.success("CALLBACK_PROFILE_PUBLISHED", "CALLBACK_PROFILE_VERSION", versionId,
                validation);
        return response(profile, version);
    }

    @Transactional
    public CallbackProfileVersionResponse deprecate(String versionId) {
        CallbackProfileVersion version = requireVersion(versionId);
        CallbackProfile profile = requireProfile(version.getCallbackProfileId());
        if (version.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
            throw new BizException(40971, "OPENAPI_CALLBACK_VERSION_NOT_PUBLISHED");
        }
        version.setLifecycleState(VersionLifecycleState.DEPRECATED);
        touch(version);
        versionMapper.updateById(version);
        return response(profile, version);
    }

    public CallbackProfileVersionResponse getVersion(String versionId) {
        CallbackProfileVersion version = requireVersion(versionId);
        return response(requireProfile(version.getCallbackProfileId()), version);
    }

    public PublishedCallback resolvePublished(String callbackKey, String tenantId,
                                               com.triobase.service.openapi.domain.enums.Environment environment) {
        CallbackProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<CallbackProfile>()
                .eq(CallbackProfile::getCallbackKey, callbackKey)
                .eq(CallbackProfile::getTenantId, tenantId)
                .eq(CallbackProfile::getLifecycleState, AssetLifecycleState.ACTIVE));
        if (profile == null) {
            throw new BizException(40470, "OPENAPI_CALLBACK_PROFILE_NOT_FOUND");
        }
        CallbackProfileVersion version = versionMapper.selectOne(
                new LambdaQueryWrapper<CallbackProfileVersion>()
                        .eq(CallbackProfileVersion::getCallbackProfileId, profile.getId())
                        .eq(CallbackProfileVersion::getEnvironment, environment)
                        .eq(CallbackProfileVersion::getLifecycleState, VersionLifecycleState.PUBLISHED)
                        .orderByDesc(CallbackProfileVersion::getVersionNumber).last("LIMIT 1"));
        if (version == null) {
            throw new BizException(40471, "OPENAPI_CALLBACK_PROFILE_VERSION_NOT_FOUND");
        }
        return new PublishedCallback(profile, version);
    }

    private ObjectNode validate(CallbackProfile profile, CallbackProfileVersion version) {
        ObjectNode result = objectMapper.createObjectNode();
        var errors = result.putArray("errors");
        if (!CALLBACK_AUTH.contains(version.getAuthenticationType())) {
            errors.add("AUTHENTICATION_TYPE_NOT_ALLOWED");
        }
        if (!validPointer(version.getPartnerEventIdPointer())) {
            errors.add("PARTNER_EVENT_POINTER_INVALID");
        }
        if (!validPointer(version.getCorrelationPointer())) {
            errors.add("CORRELATION_POINTER_INVALID");
        }
        if (!version.getSignalName().matches("[A-Za-z][A-Za-z0-9_.-]{0,127}")) {
            errors.add("SIGNAL_NAME_INVALID");
        }
        ApplicationClient client = clientMapper.selectById(version.getApplicationClientId());
        if (client == null || !profile.getTenantId().equals(client.getTenantId())
                || client.getEnvironment() != version.getEnvironment()
                || client.getLifecycleState() != ApplicationLifecycleState.ACTIVE) {
            errors.add("APPLICATION_CLIENT_NOT_ACTIVE_OR_MISMATCHED");
        }
        StructureVersion structure = structureMapper.selectById(version.getRequestStructureVersionId());
        if (structure == null || structure.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
            errors.add("REQUEST_STRUCTURE_NOT_PUBLISHED");
        }
        if (StringUtils.hasText(version.getInboundMappingVersionId())) {
            MappingVersion mapping = mappingMapper.selectById(version.getInboundMappingVersionId());
            if (mapping == null || mapping.getLifecycleState() != VersionLifecycleState.PUBLISHED) {
                errors.add("INBOUND_MAPPING_NOT_PUBLISHED");
            }
        }
        if (version.getAcknowledgementBody().length() > 2048
                || version.getAcknowledgementBody().contains("${")
                || version.getAcknowledgementBody().contains("{{")) {
            errors.add("ACKNOWLEDGEMENT_MUST_BE_FIXED_AND_SAFE");
        }
        result.put("valid", errors.isEmpty());
        return result;
    }

    private void apply(CallbackProfileVersion version, CallbackProfileVersionMutationRequest request) {
        version.setEnvironment(request.environment());
        version.setApplicationClientId(request.applicationClientId());
        version.setAuthenticationType(request.authenticationType());
        version.setSecretReference(request.secretReference());
        version.setRequestStructureVersionId(request.requestStructureVersionId());
        version.setInboundMappingVersionId(request.inboundMappingVersionId());
        version.setPartnerEventIdPointer(request.partnerEventIdPointer());
        version.setCorrelationPointer(request.correlationPointer());
        version.setCorrelationType(request.correlationType());
        version.setSignalName(request.signalName());
        version.setReplayWindowSeconds(request.replayWindowSeconds());
        version.setMaxBodyBytes(request.maxBodyBytes());
        version.setCallbackPerMinute(request.callbackPerMinute());
        version.setAcknowledgementStatus(request.acknowledgementStatus());
        version.setAcknowledgementContentType(request.acknowledgementContentType());
        version.setAcknowledgementBody(request.acknowledgementBody());
        version.setSecurityPolicy(request.securityPolicy() == null
                ? objectMapper.createObjectNode() : request.securityPolicy().deepCopy());
    }

    private CallbackProfile requireProfile(String id) {
        CallbackProfile profile = profileMapper.selectById(id);
        String tenantId = SecurityContextHolder.getTenantId();
        if (profile == null || (tenantId != null && !tenantId.equals(profile.getTenantId()))) {
            throw new BizException(40470, "OPENAPI_CALLBACK_PROFILE_NOT_FOUND");
        }
        return profile;
    }

    private CallbackProfileVersion requireVersion(String id) {
        CallbackProfileVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new BizException(40471, "OPENAPI_CALLBACK_PROFILE_VERSION_NOT_FOUND");
        }
        requireProfile(version.getCallbackProfileId());
        return version;
    }

    private void requireDraft(CallbackProfileVersion version) {
        if (version.getLifecycleState() != VersionLifecycleState.DRAFT) {
            throw new BizException(40971, "OPENAPI_PUBLISHED_CALLBACK_PROFILE_IMMUTABLE");
        }
    }

    private CallbackProfileVersionResponse response(
            CallbackProfile profile, CallbackProfileVersion version) {
        return new CallbackProfileVersionResponse(profile.getId(), version.getId(), profile.getTenantId(),
                profile.getCallbackKey(), profile.getDisplayName(), profile.getOwnerId(),
                version.getVersionNumber(), version.getLifecycleState(), version.getEnvironment(),
                version.getApplicationClientId(), version.getAuthenticationType(),
                version.getSecretReference(), version.getRequestStructureVersionId(), version.getInboundMappingVersionId(),
                version.getPartnerEventIdPointer(), version.getCorrelationPointer(),
                version.getCorrelationType(), version.getSignalName(), version.getReplayWindowSeconds(),
                version.getMaxBodyBytes(), version.getCallbackPerMinute(),
                version.getAcknowledgementStatus(), version.getAcknowledgementContentType(),
                version.getAcknowledgementBody(), version.getSecurityPolicy(), version.getValidationResult(),
                version.getPublishedBy(), version.getPublishedAt());
    }

    private String generateCallbackKey() {
        byte[] random = new byte[24];
        SECURE_RANDOM.nextBytes(random);
        return "cb_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private boolean validPointer(String pointer) {
        return StringUtils.hasText(pointer) && pointer.startsWith("/")
                && !pointer.contains("..") && !pointer.contains("*");
    }

    private void initialize(com.triobase.service.openapi.domain.model.VersionedEntity entity,
                            LocalDateTime now) {
        entity.setRowVersion(0L);
        entity.setCreatedBy(operator());
        entity.setCreatedAt(now);
        entity.setUpdatedBy(operator());
        entity.setUpdatedAt(now);
    }

    private void touch(CallbackProfileVersion version) {
        version.setUpdatedBy(operator());
        version.setUpdatedAt(LocalDateTime.now());
    }

    private String targetTenant(String requested) {
        String current = SecurityContextHolder.getTenantId();
        if (current == null) {
            if (!StringUtils.hasText(requested)) {
                throw new BizException(40070, "OPENAPI_CALLBACK_TENANT_REQUIRED");
            }
            return requested.trim();
        }
        if (StringUtils.hasText(requested) && !current.equals(requested.trim())) {
            throw new BizException(40310, "OPENAPI_CROSS_TENANT_ACCESS_DENIED");
        }
        return current;
    }

    private String operator() {
        return StringUtils.hasText(SecurityContextHolder.getUserId())
                ? SecurityContextHolder.getUserId() : "SYSTEM";
    }

    public record PublishedCallback(CallbackProfile profile, CallbackProfileVersion version) {
    }
}
