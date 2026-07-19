package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import com.triobase.service.openapi.domain.entity.CallbackNonce;
import com.triobase.service.openapi.domain.entity.CallbackProfileVersion;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.CallbackCorrelationType;
import com.triobase.service.openapi.domain.enums.CallbackInboxState;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.dto.CallbackAcknowledgement;
import com.triobase.service.openapi.action.OpenApiActionMetadata;
import com.triobase.service.openapi.infrastructure.mapper.CallbackInboxMapper;
import com.triobase.service.openapi.infrastructure.mapper.CallbackNonceMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
import com.triobase.service.openapi.integration.credential.CredentialMaterial;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import com.triobase.service.openapi.integration.http.SensitiveDataRedactor;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CallbackRuntimeService {

    private final CallbackProfileService profileService;
    private final CallbackInboxMapper inboxMapper;
    private final CallbackNonceMapper nonceMapper;
    private final IntegrationExecutionMapper executionMapper;
    private final StructureVersionMapper structureMapper;
    private final JsonPayloadValidator payloadValidator;
    private final CompiledMappingExecutor mappingExecutor;
    private final CredentialProvider credentialProvider;
    private final RuntimeBudgetService budgetService;
    private final SensitiveDataRedactor redactor;
    private final IntegrationAuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public CallbackAcknowledgement receive(
            String callbackKey, String tenantId, Environment environment,
            String applicationClientId, byte[] rawBody, CallbackHeaders headers) {
        CallbackProfileService.PublishedCallback published =
                profileService.resolvePublished(callbackKey, tenantId, environment);
        CallbackProfileVersion profile = published.version();
        if (!profile.getApplicationClientId().equals(applicationClientId)) {
            throw new BizException(40370, "OPENAPI_CALLBACK_APPLICATION_DENIED");
        }
        byte[] body = rawBody == null ? new byte[0] : rawBody;
        if (body.length > profile.getMaxBodyBytes()) {
            throw new BizException(41370, "OPENAPI_CALLBACK_BODY_TOO_LARGE");
        }
        budgetService.consumeCallback(tenantId, applicationClientId,
                callbackKey, profile.getCallbackPerMinute());
        JsonNode payload = parse(body);
        String partnerEventId = scalar(payload.at(profile.getPartnerEventIdPointer()),
                "OPENAPI_CALLBACK_EVENT_ID_REQUIRED");
        String correlation = scalar(payload.at(profile.getCorrelationPointer()),
                "OPENAPI_CALLBACK_CORRELATION_REQUIRED");
        String bodyHash = hash(body);
        CallbackInbox duplicate = findDuplicate(
                tenantId, applicationClientId, profile.getId(), partnerEventId);
        if (duplicate != null) {
            if (!bodyHash.equals(duplicate.getBodyHash())) {
                throw new BizException(40970, "OPENAPI_CALLBACK_EVENT_PAYLOAD_MISMATCH");
            }
            return acknowledgement(profile, duplicate, true);
        }
        verifyBodyHash(headers.bodyHash(), bodyHash);
        verifySignature(profile, body, headers);
        reserveNonce(tenantId, applicationClientId, profile, headers);
        validateSchema(profile, payload);
        JsonNode mapped = StringUtils.hasText(profile.getInboundMappingVersionId())
                ? mappingExecutor.execute(profile.getInboundMappingVersionId(), payload) : payload;
        CorrelationResult correlated = correlate(
                tenantId, applicationClientId, profile.getCorrelationType(), correlation);

        CallbackInbox inbox = new CallbackInbox();
        LocalDateTime now = LocalDateTime.now();
        inbox.setId(UlidGenerator.nextUlid());
        inbox.setTenantId(tenantId);
        inbox.setApplicationClientId(applicationClientId);
        inbox.setCallbackProfileVersionId(profile.getId());
        inbox.setPartnerEventId(partnerEventId);
        inbox.setCorrelationValue(correlation);
        inbox.setExecutionId(correlated.execution() == null ? null : correlated.execution().getId());
        inbox.setInboxState(correlated.reason() == null
                ? CallbackInboxState.SIGNAL_PENDING : CallbackInboxState.QUARANTINED);
        inbox.setBodyHash(bodyHash);
        inbox.setMappedPayload(redactor.payload(mapped, profile.getSecurityPolicy()));
        inbox.setSignalName(profile.getSignalName());
        inbox.setSignalAttempts(0);
        inbox.setNextSignalAt(correlated.reason() == null ? now : null);
        OpenApiActionMetadata.apply(inbox);
        inbox.setQuarantineReason(correlated.reason());
        inbox.setReceivedAt(now);
        inbox.setRetentionUntil(now.plusDays(180));
        inbox.setUpdatedAt(now);
        try {
            inboxMapper.insert(inbox);
        } catch (DuplicateKeyException race) {
            CallbackInbox raced = findDuplicate(
                    tenantId, applicationClientId, profile.getId(), partnerEventId);
            if (raced == null || !bodyHash.equals(raced.getBodyHash())) {
                throw race;
            }
            return acknowledgement(profile, raced, true);
        }
        auditService.success(correlated.reason() == null
                        ? "CALLBACK_ACCEPTED" : "CALLBACK_QUARANTINED",
                "CALLBACK_INBOX", inbox.getId(), JsonNodeFactory.instance.objectNode()
                        .put("callbackKey", callbackKey)
                        .put("quarantineReason", correlated.reason() == null ? "" : correlated.reason()));
        return acknowledgement(profile, inbox, false);
    }

    private void validateSchema(CallbackProfileVersion profile, JsonNode payload) {
        StructureVersion structure = structureMapper.selectById(profile.getRequestStructureVersionId());
        if (structure == null) {
            throw new BizException(40970, "OPENAPI_CALLBACK_STRUCTURE_MISSING");
        }
        JsonPayloadValidator.ValidationResult validation =
                payloadValidator.validate(structure.getSchemaContent(), payload);
        if (!validation.valid()) {
            throw new BizException(42270,
                    "OPENAPI_CALLBACK_SCHEMA_INVALID:" + String.join(",", validation.errors()));
        }
    }

    private void verifySignature(CallbackProfileVersion profile, byte[] body, CallbackHeaders headers) {
        try {
            long timestamp = Long.parseLong(headers.timestamp());
            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - timestamp) > profile.getReplayWindowSeconds()) {
                throw new BizException(40170, "OPENAPI_CALLBACK_TIMESTAMP_EXPIRED");
            }
            if (!StringUtils.hasText(headers.nonce()) || !StringUtils.hasText(headers.signature())) {
                throw new BizException(40170, "OPENAPI_CALLBACK_SIGNATURE_REQUIRED");
            }
            CredentialMaterial material = credentialProvider.resolve(profile.getSecretReference());
            byte[] signed = signedBytes(headers.timestamp(), headers.nonce(), body);
            boolean valid = switch (profile.getAuthenticationType()) {
                case HMAC -> verifyHmac(material, signed, headers.signature());
                case RSA -> verifyRsa(material, signed, headers.signature());
                default -> Boolean.TRUE.equals(headers.gatewayAuthenticated());
            };
            if (!valid) {
                throw new BizException(40170, "OPENAPI_CALLBACK_SIGNATURE_INVALID");
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(40170, "OPENAPI_CALLBACK_SIGNATURE_INVALID");
        }
    }

    private boolean verifyHmac(CredentialMaterial material, byte[] signed, String signature) throws Exception {
        Mac mac = Mac.getInstance(material.values().getOrDefault("algorithm", "HmacSHA256"));
        mac.init(new SecretKeySpec(material.required("secret").getBytes(StandardCharsets.UTF_8),
                mac.getAlgorithm()));
        byte[] expected = mac.doFinal(signed);
        return MessageDigest.isEqual(expected, Base64.getDecoder().decode(signature));
    }

    private boolean verifyRsa(CredentialMaterial material, byte[] signed, String signature) throws Exception {
        byte[] key = Base64.getDecoder().decode(material.required("publicKeyX509Base64"));
        Signature verifier = Signature.getInstance(
                material.values().getOrDefault("algorithm", "SHA256withRSA"));
        verifier.initVerify(KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(key)));
        verifier.update(signed);
        return verifier.verify(Base64.getDecoder().decode(signature));
    }

    private void reserveNonce(String tenantId, String applicationClientId,
                              CallbackProfileVersion profile, CallbackHeaders headers) {
        CallbackNonce nonce = new CallbackNonce();
        nonce.setId(UlidGenerator.nextUlid());
        nonce.setTenantId(tenantId);
        nonce.setApplicationClientId(applicationClientId);
        nonce.setCallbackProfileVersionId(profile.getId());
        nonce.setNonce(headers.nonce());
        nonce.setExpiresAt(LocalDateTime.ofInstant(
                Instant.ofEpochSecond(Long.parseLong(headers.timestamp())
                        + profile.getReplayWindowSeconds()), ZoneOffset.UTC));
        nonce.setCreatedAt(LocalDateTime.now());
        try {
            nonceMapper.insert(nonce);
        } catch (DuplicateKeyException replay) {
            throw new BizException(40970, "OPENAPI_CALLBACK_REPLAY_DETECTED");
        }
    }

    private CorrelationResult correlate(String tenantId, String applicationClientId,
                                        CallbackCorrelationType type, String value) {
        LambdaQueryWrapper<IntegrationExecution> query = new LambdaQueryWrapper<IntegrationExecution>()
                .eq(IntegrationExecution::getTenantId, tenantId)
                .eq(IntegrationExecution::getApplicationClientId, applicationClientId);
        switch (type) {
            case EXECUTION_ID -> query.eq(IntegrationExecution::getId, value);
            case WORKFLOW_ID -> query.eq(IntegrationExecution::getWorkflowId, value);
            case IDEMPOTENCY_KEY -> query.eq(IntegrationExecution::getIdempotencyKey, value);
        }
        List<IntegrationExecution> matches = executionMapper.selectList(query);
        if (matches.isEmpty()) {
            return new CorrelationResult(null, "UNKNOWN_CORRELATION");
        }
        if (matches.size() > 1) {
            return new CorrelationResult(null, "AMBIGUOUS_CORRELATION");
        }
        IntegrationExecution execution = matches.getFirst();
        if (execution.getExecutionState() != ExecutionState.WAITING_CALLBACK) {
            return new CorrelationResult(execution, "LATE_OR_TERMINAL_CALLBACK");
        }
        return new CorrelationResult(execution, null);
    }

    private CallbackInbox findDuplicate(String tenantId, String applicationClientId,
                                        String profileVersionId, String partnerEventId) {
        return inboxMapper.selectOne(new LambdaQueryWrapper<CallbackInbox>()
                .eq(CallbackInbox::getTenantId, tenantId)
                .eq(CallbackInbox::getApplicationClientId, applicationClientId)
                .eq(CallbackInbox::getCallbackProfileVersionId, profileVersionId)
                .eq(CallbackInbox::getPartnerEventId, partnerEventId));
    }

    private CallbackAcknowledgement acknowledgement(
            CallbackProfileVersion profile, CallbackInbox inbox, boolean duplicate) {
        return new CallbackAcknowledgement(inbox.getId(), profile.getAcknowledgementStatus(),
                profile.getAcknowledgementContentType(), profile.getAcknowledgementBody(), duplicate,
                inbox.getInboxState() == CallbackInboxState.QUARANTINED);
    }

    private JsonNode parse(byte[] body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new BizException(40070, "OPENAPI_CALLBACK_BODY_NOT_JSON");
        }
    }

    private String scalar(JsonNode node, String error) {
        if (node == null || node.isMissingNode() || node.isContainerNode()
                || !StringUtils.hasText(node.asText())) {
            throw new BizException(42270, error);
        }
        return node.asText();
    }

    private void verifyBodyHash(String supplied, String actual) {
        if (StringUtils.hasText(supplied) && !constant(supplied, actual)) {
            throw new BizException(40070, "OPENAPI_CALLBACK_BODY_HASH_MISMATCH");
        }
    }

    private byte[] signedBytes(String timestamp, String nonce, byte[] body) {
        byte[] prefix = (timestamp + "." + nonce + ".").getBytes(StandardCharsets.UTF_8);
        byte[] signed = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, signed, 0, prefix.length);
        System.arraycopy(body, 0, signed, prefix.length, body.length);
        return signed;
    }

    private String hash(byte[] body) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean constant(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    public record CallbackHeaders(
            String timestamp,
            String nonce,
            String signature,
            String bodyHash,
            Boolean gatewayAuthenticated) {
    }

    private record CorrelationResult(IntegrationExecution execution, String reason) {
    }
}
