package com.triobase.service.ops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveAnnouncementRequest {
    @NotBlank
    private String title;
    @NotBlank
    private String content;
    private String priority;
    private String targetType;
    private String targetOrgIds;
    private String targetUserIds;
}
