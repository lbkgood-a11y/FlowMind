package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuthorizationBundleResponse {
    private String tenantId;
    private String roleId;
    private String applicationResourceCode;
    private String formResourceCode;
    private String preset;
    private boolean applicable;
    private boolean applied;
    private boolean replayed;
    private long authorizationVersion;
    private List<GrantChange> changes = new ArrayList<>();

    @Data
    public static class GrantChange {
        private String resourceCode;
        private String actionCode;
        private String state;
    }
}
