package com.triobase.service.ops.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.ops.announcement.domain.AnnouncementCommand;
import com.triobase.service.ops.announcement.domain.AnnouncementTransition;
import com.triobase.service.ops.announcement.dto.AnnouncementStatistics;
import com.triobase.service.ops.announcement.dto.AnnouncementTransitionCommandRequest;
import com.triobase.service.ops.announcement.dto.AnnouncementWorkbenchRequest;
import com.triobase.service.ops.announcement.entity.AnnouncementVersionEntity;
import com.triobase.service.ops.announcement.service.AnnouncementCommandService;
import com.triobase.service.ops.announcement.service.AnnouncementReminderService;
import com.triobase.service.ops.announcement.service.AnnouncementStateCommand;
import com.triobase.service.ops.announcement.service.AnnouncementStatisticsService;
import com.triobase.service.ops.announcement.service.AnnouncementWorkbenchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 公告治理工作台 API；所有命令均从认证上下文取租户并复用状态机审计。 */
@RestController
@RequestMapping("/api/v2/announcements")
@RequiredArgsConstructor
public class AnnouncementGovernanceController {
    private final AnnouncementWorkbenchService workbenchService;
    private final AnnouncementCommandService commandService;
    private final AnnouncementStatisticsService statisticsService;
    private final AnnouncementReminderService reminderService;

    @GetMapping
    @RequirePermission("/api/v1/announcements:GET")
    public R<PageResult<AnnouncementVersionEntity>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String state) {
        return R.ok(workbenchService.page(page, size, keyword, state));
    }

    @PostMapping
    @RequirePermission("/api/v2/announcements:POST")
    public R<AnnouncementVersionEntity> create(@Valid @RequestBody AnnouncementWorkbenchRequest request) {
        return R.ok(workbenchService.create(request));
    }

    @PostMapping("/{versionId}/versions")
    @RequirePermission("/api/v2/announcements:POST")
    public R<AnnouncementVersionEntity> createVersion(@PathVariable String versionId,
                                                       @Valid @RequestBody AnnouncementWorkbenchRequest request) {
        return R.ok(workbenchService.createNextVersion(versionId, request));
    }

    @PostMapping("/{versionId}/review")
    @RequirePermission("/api/v2/announcements/*/review:POST")
    public R<AnnouncementTransition> review(@PathVariable String versionId) {
        return transition(versionId, AnnouncementCommand.SUBMIT_REVIEW, null);
    }

    @PostMapping("/{versionId}/approve")
    @RequirePermission("/api/v2/announcements/*/review:POST")
    public R<AnnouncementTransition> approve(@PathVariable String versionId,
                                              @RequestBody(required = false) AnnouncementTransitionCommandRequest request) {
        return transition(versionId, AnnouncementCommand.APPROVE, request);
    }

    @PostMapping("/{versionId}/reject")
    @RequirePermission("/api/v2/announcements/*/review:POST")
    public R<AnnouncementTransition> reject(@PathVariable String versionId,
                                             @RequestBody AnnouncementTransitionCommandRequest request) {
        return transition(versionId, AnnouncementCommand.REJECT, request);
    }

    @PostMapping("/{versionId}/publish")
    @RequirePermission("/api/v2/announcements/*/publish:POST")
    public R<AnnouncementTransition> publishScheduled(@PathVariable String versionId) {
        return transition(versionId, AnnouncementCommand.PUBLISH_SCHEDULED, null);
    }

    @PostMapping("/{versionId}/emergency-publish")
    @RequirePermission("/api/v2/announcements/*/emergency-publish:POST")
    public R<AnnouncementTransition> emergencyPublish(@PathVariable String versionId,
                                                       @RequestBody AnnouncementTransitionCommandRequest request) {
        return transition(versionId, AnnouncementCommand.EMERGENCY_PUBLISH, request);
    }

    @PostMapping("/{versionId}/withdraw")
    @RequirePermission("/api/v2/announcements/*/withdraw:POST")
    public R<AnnouncementTransition> withdraw(@PathVariable String versionId,
                                               @RequestBody AnnouncementTransitionCommandRequest request) {
        return transition(versionId, AnnouncementCommand.WITHDRAW, request);
    }

    @GetMapping("/{versionId}/statistics")
    @RequirePermission("/api/v1/announcements:GET")
    public R<AnnouncementStatistics> statistics(@PathVariable String versionId) {
        return R.ok(statisticsService.statistics(versionId, LocalDateTime.now(ZoneOffset.UTC)));
    }

    @PostMapping("/{versionId}/reminders")
    @RequirePermission("/api/v2/announcements/*/reminders:POST")
    public R<AnnouncementReminderService.ReminderBatchResult> remind(
            @PathVariable String versionId,
            @RequestParam AnnouncementReminderService.ReminderMode mode,
            @RequestParam String reminderKey,
            @RequestParam(required = false) String afterUserId,
            @RequestParam(defaultValue = "500") int limit) {
        return R.ok(reminderService.remind(versionId, reminderKey, mode, afterUserId, limit, Instant.now()));
    }

    private R<AnnouncementTransition> transition(String versionId, AnnouncementCommand command,
                                                  AnnouncementTransitionCommandRequest request) {
        return R.ok(commandService.transition(versionId, new AnnouncementStateCommand(
                command, Instant.now(), request == null ? null : request.scheduledPublishAt(),
                request == null ? null : request.reason())));
    }
}
