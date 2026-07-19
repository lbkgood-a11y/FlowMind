package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuthorizationSyncResponse {
    private String tenantId;
    private String ownerService;
    private int resourceCount;
    private int actionCount;
    private int fieldCount;
    private int guardCount;
    private Long resourceVersion;
    private Long guardTemplateVersion;
    private List<String> resourceCodes = new ArrayList<>();
}
