package com.triobase.service.lowcode.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class OwnershipReconciliationResponse {
    String tenantId;
    long unresolvedBefore;
    int scannedRecords;
    int resolvedUsers;
    int updatedRecords;
    long unresolvedAfter;
    List<String> unresolvedUserIds;
    List<String> failedUserIds;
}
