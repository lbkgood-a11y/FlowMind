package com.triobase.service.auth.dto;

import com.triobase.service.auth.entity.SysRoleAuthRelease;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleAuthorizationReleaseResponse {
    private String releaseId;
    private String roleId;
    private Long releaseNumber;
    private Long intentVersion;
    private Long catalogVersion;
    private String businessSummary;
    private String publishedBy;
    private LocalDateTime publishedAt;

    public static RoleAuthorizationReleaseResponse from(SysRoleAuthRelease release) {
        RoleAuthorizationReleaseResponse response = new RoleAuthorizationReleaseResponse();
        response.setReleaseId(release.getId());
        response.setRoleId(release.getRoleId());
        response.setReleaseNumber(release.getReleaseNumber());
        response.setIntentVersion(release.getIntentVersion());
        response.setCatalogVersion(release.getCatalogVersion());
        response.setBusinessSummary(release.getBusinessSummary());
        response.setPublishedBy(release.getPublishedBy());
        response.setPublishedAt(release.getPublishedAt());
        return response;
    }
}
