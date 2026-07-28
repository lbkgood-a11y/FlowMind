package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReplaceRoleFunctionGrantsRequest {
    private String tenantId;
    private Long expectedGrantVersion;
    private List<GrantItem> grants;

    @Data
    public static class GrantItem {
        private String resourceCode;
        private String actionCode;
        private String description;
    }
}
