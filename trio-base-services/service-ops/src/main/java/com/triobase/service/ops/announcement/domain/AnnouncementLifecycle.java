package com.triobase.service.ops.announcement.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * 公告版本的确定性状态机。
 *
 * <p>已发布、撤回、过期和已替代版本均不可回到可编辑状态。修改这些版本必须创建新的版本，
 * 以保留接收人看到的原始正文、受众政策和确认声明。紧急发布是唯一允许跳过普通审核的路径，
 * 且必须由应用层验证独立权限并提供审计原因。</p>
 */
public final class AnnouncementLifecycle {

    private static final Set<AnnouncementState> TERMINAL_EVIDENCE_STATES = EnumSet.of(
            AnnouncementState.EXPIRED, AnnouncementState.WITHDRAWN, AnnouncementState.SUPERSEDED);

    private AnnouncementLifecycle() {
    }

    public static AnnouncementTransition transition(AnnouncementTransitionRequest request) {
        requireRequest(request);
        AnnouncementState next = switch (request.command()) {
            case SUBMIT_REVIEW -> requireFrom(request, AnnouncementState.PENDING_REVIEW,
                    AnnouncementState.DRAFT, AnnouncementState.REJECTED);
            case APPROVE -> approve(request);
            case REJECT -> reject(request);
            case PUBLISH_SCHEDULED -> requireFrom(request, AnnouncementState.PUBLISHED,
                    AnnouncementState.SCHEDULED);
            case EMERGENCY_PUBLISH -> emergencyPublish(request);
            case EXPIRE -> requireFrom(request, AnnouncementState.EXPIRED, AnnouncementState.PUBLISHED);
            case WITHDRAW -> withdraw(request);
            case SUPERSEDE -> requireFrom(request, AnnouncementState.SUPERSEDED, AnnouncementState.PUBLISHED);
            case REVISE -> requireFrom(request, AnnouncementState.DRAFT, AnnouncementState.REJECTED);
        };
        return new AnnouncementTransition(
                request.currentState(), next, request.command(), request.occurredAt(),
                request.command() == AnnouncementCommand.EMERGENCY_PUBLISH, normalizeReason(request.reason()));
    }

    private static AnnouncementState approve(AnnouncementTransitionRequest request) {
        requireFrom(request, AnnouncementState.PENDING_REVIEW, AnnouncementState.PENDING_REVIEW);
        Instant schedule = request.scheduledPublishAt();
        return schedule != null && schedule.isAfter(request.occurredAt())
                ? AnnouncementState.SCHEDULED : AnnouncementState.PUBLISHED;
    }

    private static AnnouncementState reject(AnnouncementTransitionRequest request) {
        requireReason(request, "ANNOUNCEMENT_REJECTION_REASON_REQUIRED");
        return requireFrom(request, AnnouncementState.REJECTED, AnnouncementState.PENDING_REVIEW);
    }

    private static AnnouncementState emergencyPublish(AnnouncementTransitionRequest request) {
        if (!request.emergencyAuthorized()) {
            throw new IllegalStateException("ANNOUNCEMENT_EMERGENCY_PERMISSION_REQUIRED");
        }
        requireReason(request, "ANNOUNCEMENT_EMERGENCY_REASON_REQUIRED");
        return requireFrom(request, AnnouncementState.PUBLISHED,
                AnnouncementState.DRAFT, AnnouncementState.PENDING_REVIEW,
                AnnouncementState.REJECTED, AnnouncementState.SCHEDULED);
    }

    private static AnnouncementState withdraw(AnnouncementTransitionRequest request) {
        requireReason(request, "ANNOUNCEMENT_WITHDRAWAL_REASON_REQUIRED");
        return requireFrom(request, AnnouncementState.WITHDRAWN,
                AnnouncementState.SCHEDULED, AnnouncementState.PUBLISHED);
    }

    private static AnnouncementState requireFrom(AnnouncementTransitionRequest request,
                                                 AnnouncementState next,
                                                 AnnouncementState... allowed) {
        if (TERMINAL_EVIDENCE_STATES.contains(request.currentState())) {
            throw new IllegalStateException("ANNOUNCEMENT_TERMINAL_VERSION_IMMUTABLE");
        }
        for (AnnouncementState state : allowed) {
            if (state == request.currentState()) {
                return next;
            }
        }
        throw new IllegalStateException(
                "ANNOUNCEMENT_TRANSITION_NOT_ALLOWED:" + request.currentState() + "->" + request.command());
    }

    private static void requireRequest(AnnouncementTransitionRequest request) {
        if (request == null || request.currentState() == null
                || request.command() == null || request.occurredAt() == null) {
            throw new IllegalArgumentException("ANNOUNCEMENT_TRANSITION_CONTEXT_REQUIRED");
        }
    }

    private static void requireReason(AnnouncementTransitionRequest request, String error) {
        if (normalizeReason(request.reason()) == null) {
            throw new IllegalArgumentException(error);
        }
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }
}
