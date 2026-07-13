package com.triobase.service.workflow.dto;

import lombok.Data;

@Data
public class AddSignRequest {
    private String assigneeId;
    private String assigneeName;
}
