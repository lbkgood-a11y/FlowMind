package com.triobase.service.lowcode.dto;

import com.triobase.service.lowcode.entity.LcAuthorizationOutbox;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuthorizationPublicationStatusResponse {
    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private Integer aggregateVersion;
    private String operation;
    private String snapshotHash;
    private String status;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private Long acknowledgedRevision;
    private LocalDateTime acknowledgedAt;
    private String lastError;

    public static AuthorizationPublicationStatusResponse from(LcAuthorizationOutbox outbox) {
        AuthorizationPublicationStatusResponse response = new AuthorizationPublicationStatusResponse();
        response.setEventId(outbox.getId());
        response.setAggregateType(outbox.getAggregateType());
        response.setAggregateId(outbox.getAggregateId());
        response.setAggregateVersion(outbox.getAggregateVersion());
        response.setOperation(outbox.getOperation());
        response.setSnapshotHash(outbox.getSnapshotHash());
        response.setStatus(outbox.getStatus());
        response.setAttemptCount(outbox.getAttemptCount());
        response.setNextRetryAt(outbox.getNextRetryAt());
        response.setAcknowledgedRevision(outbox.getAcknowledgedRevision());
        response.setAcknowledgedAt(outbox.getAcknowledgedAt());
        response.setLastError(outbox.getLastError());
        return response;
    }
}
