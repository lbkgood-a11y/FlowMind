package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.dto.authz.AuthorizationResourceSyncRequest;
import com.triobase.service.lowcode.dto.ApplicationActionRequest;
import com.triobase.service.lowcode.dto.ApplicationPageRequest;
import com.triobase.service.lowcode.dto.FormFieldSchemaRequest;
import com.triobase.service.lowcode.dto.AuthorizationPublicationStatusResponse;
import com.triobase.service.lowcode.entity.LcApplicationVersion;
import com.triobase.service.lowcode.entity.LcAuthorizationOutbox;
import com.triobase.service.lowcode.entity.LcFormDefinition;
import com.triobase.service.lowcode.mapper.ApplicationVersionMapper;
import com.triobase.service.lowcode.mapper.FormDefinitionMapper;
import com.triobase.service.lowcode.mapper.LcAuthorizationOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthorizationPublicationService {

    public static final String PENDING = "PENDING";
    public static final String SYNCED = "SYNCED";
    public static final String RETRYING = "RETRYING";
    public static final String FAILED = "FAILED";
    private static final int MAX_ATTEMPTS = 10;
    private static final Logger log = LoggerFactory.getLogger(AuthorizationPublicationService.class);

    private final LcAuthorizationOutboxMapper outboxMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final ApplicationVersionMapper applicationVersionMapper;
    private final AuthorizationResourceSyncClient syncClient;
    private final ObjectMapper objectMapper;

    public void enqueuePublishedForm(LcFormDefinition definition, List<FormFieldSchemaRequest> fields) {
        enqueue("FORM", definition.getId(), definition.getVersion(), "PUBLISH",
                definition.getTenantId(), definition.getSchemaHash(),
                syncClient.publishedFormRequest(definition, fields));
    }

    public void enqueueOfflineForm(LcFormDefinition definition) {
        AuthorizationResourceSyncRequest request = syncClient.offlineFormRequest(definition);
        if (request != null) {
            enqueue("FORM", definition.getId(), definition.getVersion(), "OFFLINE",
                    definition.getTenantId(), definition.getSchemaHash(), request);
        }
    }

    public void enqueuePublishedApplication(LcApplicationVersion version,
                                            List<ApplicationPageRequest> pages,
                                            List<ApplicationActionRequest> actions) {
        enqueue("APPLICATION", version.getId(), version.getVersion(), "PUBLISH",
                version.getTenantId(), version.getMetadataHash(),
                syncClient.publishedApplicationRequest(version, pages, actions));
    }

    public void enqueueOfflineApplication(LcApplicationVersion version) {
        AuthorizationResourceSyncRequest request = syncClient.offlineApplicationRequest(version);
        if (request != null) {
            enqueue("APPLICATION", version.getId(), version.getVersion(), "OFFLINE",
                    version.getTenantId(), version.getMetadataHash(), request);
        }
    }

    private void enqueue(String aggregateType,
                         String aggregateId,
                         Integer aggregateVersion,
                         String operation,
                         String tenantId,
                         String snapshotHash,
                         AuthorizationResourceSyncRequest request) {
        if (snapshotHash == null || snapshotHash.isBlank()) {
            throw new BizException(40990, "LOWCODE_AUTHORIZATION_SNAPSHOT_REQUIRED");
        }
        String eventId = UlidGenerator.nextUlid();
        request.setEventId(eventId);
        request.setAggregateType(aggregateType);
        request.setAggregateId(aggregateId);
        request.setAggregateVersion(aggregateVersion);
        request.setOperation(operation);
        request.setSnapshotHash(snapshotHash);

        LcAuthorizationOutbox outbox = new LcAuthorizationOutbox();
        outbox.setId(eventId);
        outbox.setTenantId(tenantId);
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setAggregateVersion(aggregateVersion);
        outbox.setOperation(operation);
        outbox.setSnapshotHash(snapshotHash);
        outbox.setPayloadJson(write(request));
        outbox.setStatus(PENDING);
        outbox.setAttemptCount(0);
        outboxMapper.insert(outbox);
    }

    @Transactional
    public int dispatchPending(int limit) {
        // Reclaim records abandoned by a crashed dispatcher after the lease expires.
        outboxMapper.update(null, new LambdaUpdateWrapper<LcAuthorizationOutbox>()
                .eq(LcAuthorizationOutbox::getStatus, "RUNNING")
                .lt(LcAuthorizationOutbox::getLockedAt, LocalDateTime.now().minusMinutes(2))
                .set(LcAuthorizationOutbox::getStatus, RETRYING)
                .set(LcAuthorizationOutbox::getNextRetryAt, LocalDateTime.now()));
        List<LcAuthorizationOutbox> records = outboxMapper.selectList(
                new LambdaQueryWrapper<LcAuthorizationOutbox>()
                        .in(LcAuthorizationOutbox::getStatus, List.of(PENDING, RETRYING))
                        .and(q -> q.isNull(LcAuthorizationOutbox::getNextRetryAt)
                                .or().le(LcAuthorizationOutbox::getNextRetryAt, LocalDateTime.now()))
                        .orderByAsc(LcAuthorizationOutbox::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 100))));
        int dispatched = 0;
        for (LcAuthorizationOutbox outbox : records) {
            if (!claim(outbox)) {
                continue;
            }
            try {
                AuthorizationResourceSyncRequest request = objectMapper.readValue(
                        outbox.getPayloadJson(), AuthorizationResourceSyncRequest.class);
                long revision = syncClient.synchronize(request);
                acknowledge(outbox, revision);
                dispatched++;
            } catch (Exception exception) {
                fail(outbox, exception);
            }
        }
        return dispatched;
    }

    private boolean claim(LcAuthorizationOutbox outbox) {
        int updated = outboxMapper.update(null, new LambdaUpdateWrapper<LcAuthorizationOutbox>()
                .eq(LcAuthorizationOutbox::getId, outbox.getId())
                .in(LcAuthorizationOutbox::getStatus, List.of(PENDING, RETRYING))
                .set(LcAuthorizationOutbox::getStatus, "RUNNING")
                .set(LcAuthorizationOutbox::getLockedAt, LocalDateTime.now())
                .set(LcAuthorizationOutbox::getAttemptCount, outbox.getAttemptCount() + 1));
        if (updated > 0) {
            outbox.setAttemptCount(outbox.getAttemptCount() + 1);
        }
        return updated > 0;
    }

    private void acknowledge(LcAuthorizationOutbox outbox, long revision) {
        LocalDateTime now = LocalDateTime.now();
        outboxMapper.update(null, new LambdaUpdateWrapper<LcAuthorizationOutbox>()
                .eq(LcAuthorizationOutbox::getId, outbox.getId())
                .set(LcAuthorizationOutbox::getStatus, "ACKNOWLEDGED")
                .set(LcAuthorizationOutbox::getAcknowledgedRevision, revision)
                .set(LcAuthorizationOutbox::getAcknowledgedAt, now)
                .set(LcAuthorizationOutbox::getLastError, null));
        if ("FORM".equals(outbox.getAggregateType())) {
            formDefinitionMapper.update(null, new LambdaUpdateWrapper<LcFormDefinition>()
                    .eq(LcFormDefinition::getId, outbox.getAggregateId())
                    .eq(LcFormDefinition::getVersion, outbox.getAggregateVersion())
                    .eq(LcFormDefinition::getAuthorizationSnapshotHash, outbox.getSnapshotHash())
                    .set(LcFormDefinition::getAuthorizationStatus, SYNCED)
                    .set(LcFormDefinition::getAuthorizationRevision, revision)
                    .set(LcFormDefinition::getAuthorizationSyncedAt, now));
        } else {
            applicationVersionMapper.update(null, new LambdaUpdateWrapper<LcApplicationVersion>()
                    .eq(LcApplicationVersion::getId, outbox.getAggregateId())
                    .eq(LcApplicationVersion::getVersion, outbox.getAggregateVersion())
                    .eq(LcApplicationVersion::getAuthorizationSnapshotHash, outbox.getSnapshotHash())
                    .set(LcApplicationVersion::getAuthorizationStatus, SYNCED)
                    .set(LcApplicationVersion::getAuthorizationRevision, revision)
                    .set(LcApplicationVersion::getAuthorizationSyncedAt, now));
        }
        log.info("Lowcode authorization publication acknowledged: eventId={}, aggregateType={}, aggregateId={}, revision={}",
                outbox.getId(), outbox.getAggregateType(), outbox.getAggregateId(), revision);
    }

    private void fail(LcAuthorizationOutbox outbox, Exception exception) {
        boolean exhausted = outbox.getAttemptCount() >= MAX_ATTEMPTS;
        long delaySeconds = Math.min(900L, 1L << Math.min(outbox.getAttemptCount(), 9));
        outboxMapper.update(null, new LambdaUpdateWrapper<LcAuthorizationOutbox>()
                .eq(LcAuthorizationOutbox::getId, outbox.getId())
                .set(LcAuthorizationOutbox::getStatus, exhausted ? FAILED : RETRYING)
                .set(LcAuthorizationOutbox::getNextRetryAt,
                        exhausted ? null : LocalDateTime.now().plusSeconds(delaySeconds))
                .set(LcAuthorizationOutbox::getLastError, truncate(exception.getMessage())));
        log.warn("Lowcode authorization publication failed: eventId={}, attempt={}, exhausted={}, error={}",
                outbox.getId(), outbox.getAttemptCount(), exhausted, exception.getMessage());
    }

    private String write(AuthorizationResourceSyncRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new BizException(50090, "LOWCODE_AUTHORIZATION_EVENT_SERIALIZATION_FAILED");
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "UNKNOWN";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public List<AuthorizationPublicationStatusResponse> listCurrentTenant() {
        return outboxMapper.selectList(new LambdaQueryWrapper<LcAuthorizationOutbox>()
                        .eq(LcAuthorizationOutbox::getTenantId, currentTenantId())
                        .orderByDesc(LcAuthorizationOutbox::getCreatedAt)
                        .last("LIMIT 200"))
                .stream()
                .map(AuthorizationPublicationStatusResponse::from)
                .toList();
    }

    @Transactional
    public void retry(String eventId) {
        LcAuthorizationOutbox outbox = requireTenantEvent(eventId);
        if ("ACKNOWLEDGED".equals(outbox.getStatus())) {
            return;
        }
        outboxMapper.update(null, new LambdaUpdateWrapper<LcAuthorizationOutbox>()
                .eq(LcAuthorizationOutbox::getId, eventId)
                .eq(LcAuthorizationOutbox::getTenantId, currentTenantId())
                .set(LcAuthorizationOutbox::getStatus, PENDING)
                .set(LcAuthorizationOutbox::getAttemptCount, 0)
                .set(LcAuthorizationOutbox::getNextRetryAt, null)
                .set(LcAuthorizationOutbox::getLastError, null));
    }

    @Transactional
    public AuthorizationPublicationStatusResponse reconcile(String eventId) {
        LcAuthorizationOutbox source = requireTenantEvent(eventId);
        AuthorizationResourceSyncRequest request;
        try {
            request = objectMapper.readValue(source.getPayloadJson(), AuthorizationResourceSyncRequest.class);
        } catch (JsonProcessingException exception) {
            throw new BizException(50091, "LOWCODE_AUTHORIZATION_EVENT_INVALID");
        }
        String repairId = UlidGenerator.nextUlid();
        request.setEventId(repairId);
        LcAuthorizationOutbox repair = new LcAuthorizationOutbox();
        repair.setId(repairId);
        repair.setTenantId(source.getTenantId());
        repair.setAggregateType(source.getAggregateType());
        repair.setAggregateId(source.getAggregateId());
        repair.setAggregateVersion(source.getAggregateVersion());
        repair.setOperation(source.getOperation());
        repair.setSnapshotHash(source.getSnapshotHash());
        repair.setPayloadJson(write(request));
        repair.setStatus(PENDING);
        repair.setAttemptCount(0);
        outboxMapper.insert(repair);
        return AuthorizationPublicationStatusResponse.from(repair);
    }

    private LcAuthorizationOutbox requireTenantEvent(String eventId) {
        LcAuthorizationOutbox outbox = outboxMapper.selectOne(new LambdaQueryWrapper<LcAuthorizationOutbox>()
                .eq(LcAuthorizationOutbox::getId, eventId)
                .eq(LcAuthorizationOutbox::getTenantId, currentTenantId())
                .last("LIMIT 1"));
        if (outbox == null) {
            throw new BizException(40490, "LOWCODE_AUTHORIZATION_EVENT_NOT_FOUND");
        }
        return outbox;
    }

    private String currentTenantId() {
        String tenantId = SecurityContextHolder.getTenantId();
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId.trim();
    }
}
