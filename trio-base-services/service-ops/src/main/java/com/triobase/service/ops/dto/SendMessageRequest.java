package com.triobase.service.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SendMessageRequest {
    @NotBlank
    private String title;
    @NotBlank
    private String content;
    private String messageType;
    private String sourceType;
    private String sourceId;
    @NotEmpty
    private List<String> recipientUserIds;
}
