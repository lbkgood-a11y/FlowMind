package com.triobase.service.ops.announcement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** 工作台只接受类型化选择结果；租户范围由服务端认证上下文注入。 */
public record AnnouncementWorkbenchRequest(
        @NotBlank @Size(max = 160) String title,
        @NotBlank String content,
        String priority,
        boolean confirmationRequired,
        @Size(max = 512) String confirmationStatement,
        LocalDateTime confirmationDeadline,
        LocalDateTime effectiveUntil,
        LocalDateTime pinFrom,
        LocalDateTime pinUntil,
        @NotEmpty List<@Valid AudienceRule> audience) {

    public record AudienceRule(
            @NotNull AudienceType type,
            List<@NotBlank String> subjectIds,
            boolean includeDescendants,
            String resolverKey) {
    }

    public enum AudienceType { ALL, ORGANIZATION, ROLE, USER, DYNAMIC_PARTICIPANT }
}
