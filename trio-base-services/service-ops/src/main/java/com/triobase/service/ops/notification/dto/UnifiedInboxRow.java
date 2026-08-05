package com.triobase.service.ops.notification.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UnifiedInboxRow {
    private String id;
    private String itemType;
    private String title;
    private String summary;
    private LocalDateTime receivedAt;
    private LocalDateTime readAt;
    private LocalDateTime archivedAt;
    private Boolean withdrawn;
    private Boolean taskRelated;
    private String sourceOwner;
    private String resourceType;
    private String resourceId;
    private String resourceKey;
    private String actionId;
}
