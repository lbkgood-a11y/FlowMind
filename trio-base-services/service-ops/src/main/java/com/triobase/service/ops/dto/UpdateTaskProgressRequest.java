package com.triobase.service.ops.dto;

import lombok.Data;

@Data
public class UpdateTaskProgressRequest {
    private String status;
    private Integer progress;
    private String resultFileId;
    private String failureFileId;
    private Integer successCount;
    private Integer failureCount;
    private String failureReason;
}
