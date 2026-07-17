package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import com.triobase.service.openapi.domain.entity.CallbackProfile;
import com.triobase.service.openapi.domain.entity.CallbackProfileVersion;
import com.triobase.service.openapi.domain.entity.IntegrationExecution;
import com.triobase.service.openapi.domain.entity.StructureVersion;
import com.triobase.service.openapi.domain.enums.AuthenticationType;
import com.triobase.service.openapi.domain.enums.CallbackCorrelationType;
import com.triobase.service.openapi.domain.enums.CallbackInboxState;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.infrastructure.mapper.CallbackInboxMapper;
import com.triobase.service.openapi.infrastructure.mapper.CallbackNonceMapper;
import com.triobase.service.openapi.infrastructure.mapper.IntegrationExecutionMapper;
import com.triobase.service.openapi.infrastructure.mapper.StructureVersionMapper;
import com.triobase.service.openapi.integration.credential.CredentialMaterial;
import com.triobase.service.openapi.integration.credential.CredentialProvider;
import com.triobase.service.openapi.integration.http.SensitiveDataRedactor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CallbackRuntimeServiceTest {

    @Mock private CallbackProfileService profiles;
    @Mock private CallbackInboxMapper inboxMapper;
    @Mock private CallbackNonceMapper nonceMapper;
    @Mock private IntegrationExecutionMapper executionMapper;
    @Mock private StructureVersionMapper structureMapper;
    @Mock private CompiledMappingExecutor mappingExecutor;
    @Mock private CredentialProvider credentialProvider;
    @Mock private RuntimeBudgetService budgets;
    @Mock private IntegrationAuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private CallbackRuntimeService service;
    private CallbackProfileVersion version;

    @BeforeEach
    void setUp() throws Exception {
        service = new CallbackRuntimeService(profiles, inboxMapper, nonceMapper, executionMapper,
                structureMapper, new JsonPayloadValidator(), mappingExecutor, credentialProvider,
                budgets, new SensitiveDataRedactor(), auditService, objectMapper);
        CallbackProfile profile = new CallbackProfile();
        profile.setId("profile-1");
        profile.setTenantId("tenant-a");
        version = version();
        when(profiles.resolvePublished("cb_key", "tenant-a", Environment.PROD))
                .thenReturn(new CallbackProfileService.PublishedCallback(profile, version));
    }

    @Test
    void persistsBeforeAcknowledgementAndQueuesSignalWithRedactedPayload() throws Exception {
        arrangeValidCallback(waitingExecution());
        byte[] body = body("event-1", "execution-1");

        var acknowledgement = service.receive("cb_key", "tenant-a", Environment.PROD,
                "client-1", body, headers(body, Instant.now().getEpochSecond(), "nonce-1"));

        assertThat(acknowledgement.status()).isEqualTo(202);
        assertThat(acknowledgement.duplicate()).isFalse();
        ArgumentCaptor<CallbackInbox> captor = ArgumentCaptor.forClass(CallbackInbox.class);
        verify(inboxMapper).insert(captor.capture());
        assertThat(captor.getValue().getInboxState()).isEqualTo(CallbackInboxState.SIGNAL_PENDING);
        assertThat(captor.getValue().getExecutionId()).isEqualTo("execution-1");
        assertThat(captor.getValue().getMappedPayload().path("token").asText())
                .isEqualTo("***REDACTED***");
        verify(auditService).success(org.mockito.ArgumentMatchers.eq("CALLBACK_ACCEPTED"),
                org.mockito.ArgumentMatchers.eq("CALLBACK_INBOX"), any(), any());
    }

    @Test
    void duplicateEventReturnsIdempotentAcknowledgementWithoutSecondSignalRecord() throws Exception {
        byte[] body = body("event-1", "execution-1");
        CallbackInbox existing = new CallbackInbox();
        existing.setId("inbox-1");
        existing.setBodyHash(hash(body));
        existing.setInboxState(CallbackInboxState.SIGNALLED);
        when(inboxMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        var acknowledgement = service.receive("cb_key", "tenant-a", Environment.PROD,
                "client-1", body, headers(body, Instant.now().getEpochSecond(), "same-nonce"));

        assertThat(acknowledgement.duplicate()).isTrue();
        verify(nonceMapper, never()).insert(any(com.triobase.service.openapi.domain.entity.CallbackNonce.class));
        verify(inboxMapper, never()).insert(any(CallbackInbox.class));
    }

    @Test
    void rejectsInvalidSignatureAndExpiredTimestamp() throws Exception {
        when(credentialProvider.resolve("vault:callback"))
                .thenReturn(new CredentialMaterial(Map.of("secret", "callback-secret")));
        byte[] body = body("event-1", "execution-1");
        CallbackRuntimeService.CallbackHeaders invalid = new CallbackRuntimeService.CallbackHeaders(
                Long.toString(Instant.now().getEpochSecond()), "nonce", "invalid", hash(body), true);
        assertThatThrownBy(() -> service.receive(
                "cb_key", "tenant-a", Environment.PROD, "client-1", body, invalid))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_CALLBACK_SIGNATURE_INVALID");

        long expired = Instant.now().minusSeconds(1000).getEpochSecond();
        assertThatThrownBy(() -> service.receive("cb_key", "tenant-a", Environment.PROD,
                "client-1", body, headers(body, expired, "nonce-expired")))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_CALLBACK_TIMESTAMP_EXPIRED");
    }

    @Test
    void quarantinesUnknownAndLateCorrelation() throws Exception {
        arrangeValidCallback(null);
        byte[] unknown = body("event-unknown", "missing");
        var unknownAck = service.receive("cb_key", "tenant-a", Environment.PROD,
                "client-1", unknown, headers(unknown, Instant.now().getEpochSecond(), "nonce-u"));
        assertThat(unknownAck.quarantined()).isTrue();

        when(inboxMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        IntegrationExecution completed = waitingExecution();
        completed.setExecutionState(ExecutionState.SUCCEEDED);
        when(executionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(completed));
        byte[] late = body("event-late", "execution-1");
        var lateAck = service.receive("cb_key", "tenant-a", Environment.PROD,
                "client-1", late, headers(late, Instant.now().getEpochSecond(), "nonce-l"));
        assertThat(lateAck.quarantined()).isTrue();
    }

    @Test
    void rejectsWhenCallbackRateBudgetIsExhausted() throws Exception {
        byte[] body = body("event-1", "execution-1");
        doThrow(new BizException(42901, "OPENAPI_CALLBACK_BUDGET_EXHAUSTED"))
                .when(budgets).consumeCallback("tenant-a", "client-1", "cb_key", 60);

        assertThatThrownBy(() -> service.receive("cb_key", "tenant-a", Environment.PROD,
                "client-1", body, headers(body, Instant.now().getEpochSecond(), "nonce-rate")))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_CALLBACK_BUDGET_EXHAUSTED");
    }

    @Test
    void rejectsReplayedNonceBeforeInboxPersistence() throws Exception {
        when(credentialProvider.resolve("vault:callback"))
                .thenReturn(new CredentialMaterial(Map.of("secret", "callback-secret")));
        when(nonceMapper.insert(any(com.triobase.service.openapi.domain.entity.CallbackNonce.class)))
                .thenThrow(new DuplicateKeyException("duplicate nonce"));
        byte[] body = body("event-replay", "execution-1");

        assertThatThrownBy(() -> service.receive("cb_key", "tenant-a", Environment.PROD,
                "client-1", body, headers(body, Instant.now().getEpochSecond(), "nonce-replay")))
                .isInstanceOf(BizException.class)
                .hasMessage("OPENAPI_CALLBACK_REPLAY_DETECTED");
        verify(inboxMapper, never()).insert(any(CallbackInbox.class));
    }

    private void arrangeValidCallback(IntegrationExecution execution) throws Exception {
        when(credentialProvider.resolve("vault:callback"))
                .thenReturn(new CredentialMaterial(Map.of("secret", "callback-secret")));
        StructureVersion structure = new StructureVersion();
        structure.setSchemaContent(objectMapper.readTree("""
                {"type":"object","required":["eventId","executionId"],"properties":{
                  "eventId":{"type":"string"},"executionId":{"type":"string"}
                }}
                """));
        when(structureMapper.selectById("structure-v1")).thenReturn(structure);
        when(executionMapper.selectList(any(Wrapper.class)))
                .thenReturn(execution == null ? List.of() : List.of(execution));
        when(inboxMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(inboxMapper.insert(any(CallbackInbox.class))).thenReturn(1);
        when(nonceMapper.insert(any(com.triobase.service.openapi.domain.entity.CallbackNonce.class))).thenReturn(1);
    }

    private CallbackProfileVersion version() throws Exception {
        CallbackProfileVersion value = new CallbackProfileVersion();
        value.setId("profile-v1");
        value.setApplicationClientId("client-1");
        value.setAuthenticationType(AuthenticationType.HMAC);
        value.setSecretReference("vault:callback");
        value.setRequestStructureVersionId("structure-v1");
        value.setPartnerEventIdPointer("/eventId");
        value.setCorrelationPointer("/executionId");
        value.setCorrelationType(CallbackCorrelationType.EXECUTION_ID);
        value.setSignalName("partner-result");
        value.setReplayWindowSeconds(300L);
        value.setMaxBodyBytes(1024L);
        value.setCallbackPerMinute(60L);
        value.setAcknowledgementStatus(202);
        value.setAcknowledgementContentType("application/json");
        value.setAcknowledgementBody("{\"accepted\":true}");
        value.setSecurityPolicy(objectMapper.readTree("{\"sensitivePointers\":[\"/token\"]}"));
        return value;
    }

    private IntegrationExecution waitingExecution() {
        IntegrationExecution execution = new IntegrationExecution();
        execution.setId("execution-1");
        execution.setTenantId("tenant-a");
        execution.setApplicationClientId("client-1");
        execution.setExecutionState(ExecutionState.WAITING_CALLBACK);
        return execution;
    }

    private byte[] body(String eventId, String executionId) {
        return ("{\"eventId\":\"" + eventId + "\",\"executionId\":\"" + executionId
                + "\",\"token\":\"secret-value\"}").getBytes(StandardCharsets.UTF_8);
    }

    private CallbackRuntimeService.CallbackHeaders headers(byte[] body, long timestamp, String nonce)
            throws Exception {
        String timestampText = Long.toString(timestamp);
        byte[] prefix = (timestampText + "." + nonce + ".").getBytes(StandardCharsets.UTF_8);
        byte[] signed = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, signed, 0, prefix.length);
        System.arraycopy(body, 0, signed, prefix.length, body.length);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("callback-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return new CallbackRuntimeService.CallbackHeaders(timestampText, nonce,
                Base64.getEncoder().encodeToString(mac.doFinal(signed)), hash(body), true);
    }

    private String hash(byte[] body) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256").digest(body));
    }
}
