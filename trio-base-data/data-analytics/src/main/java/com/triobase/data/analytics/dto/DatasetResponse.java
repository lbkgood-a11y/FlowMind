package com.triobase.data.analytics.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DatasetResponse {
    private String id;
    private String datasetKey;
    private String name;
    private String datasetType;
    private String ownerId;
    private String ownerName;
    private String status;
    private String backingTable;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<DatasetFieldResponse> fields;
}
