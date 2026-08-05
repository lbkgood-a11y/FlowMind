package com.triobase.service.ops.announcement.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.trace.TraceUtil;
import com.triobase.service.ops.announcement.domain.AnnouncementLifecycle;
import com.triobase.service.ops.announcement.domain.AnnouncementState;
import com.triobase.service.ops.announcement.domain.AnnouncementTransition;
import com.triobase.service.ops.announcement.domain.AnnouncementTransitionRequest;
import com.triobase.service.ops.announcement.entity.AnnouncementTransitionEntity;
import com.triobase.service.ops.announcement.entity.AnnouncementVersionEntity;
import com.triobase.service.ops.announcement.mapper.AnnouncementTransitionMapper;
import com.triobase.service.ops.announcement.mapper.AnnouncementVersionMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 在同一事务中推进公告版本并追加状态证据。
 *
 * <p>更新条件同时包含租户、版本和原状态，确保并发审核或发布最多一个成功。应用层权限和状态机
 * 校验不能替代该原子条件；否则两个合法请求仍可能覆盖彼此的审计顺序。</p>
 */
@Service
@RequiredArgsConstructor
public class AnnouncementCommandService {

    public static final String EMERGENCY_PERMISSION = "OPS.ANNOUNCEMENT.EMERGENCY_PUBLISH";

    private final AnnouncementVersionMapper versionMapper;
    private final AnnouncementTransitionMapper transitionMapper;
    private final RequestContextService contextService;

    @Transactional
    public AnnouncementTransition transition(String versionId, AnnouncementStateCommand command) {
        AnnouncementVersionEntity version = requireVersion(versionId);
        String actorId = contextService.userId();
        boolean emergency = contextService.hasPermission(EMERGENCY_PERMISSION);
        AnnouncementTransition transition;
        try {
            transition = AnnouncementLifecycle.transition(new AnnouncementTransitionRequest(
                    AnnouncementState.valueOf(version.getLifecycleState()),
                    command.command(),
                    command.occurredAt(),
                    command.scheduledPublishAt(),
                    command.reason(),
                    !actorId.equals(version.getCreatedBy()),
                    emergency));
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new BizException(45202, e.getMessage());
        }

        LocalDateTime occurredAt = LocalDateTime.ofInstant(command.occurredAt(), ZoneOffset.UTC);
        UpdateWrapper<AnnouncementVersionEntity> update =
                new UpdateWrapper<AnnouncementVersionEntity>()
                        .eq("id", versionId)
                        .eq("tenant_id", contextService.tenantId())
                        .eq("lifecycle_state", transition.fromState().name())
                        .set("lifecycle_state", transition.toState().name())
                        .set(transition.toState() == AnnouncementState.PUBLISHED,
                                "published_at", occurredAt)
                        .set(transition.toState() == AnnouncementState.SCHEDULED,
                                "scheduled_publish_at",
                                command.scheduledPublishAt() == null ? null
                                        : LocalDateTime.ofInstant(command.scheduledPublishAt(), ZoneOffset.UTC))
                        .set(transition.toState() == AnnouncementState.WITHDRAWN,
                                "withdrawal_reason", transition.reason())
                        .set(transition.toState() == AnnouncementState.WITHDRAWN,
                                "withdrawn_at", occurredAt);
        if (versionMapper.update(null, update) != 1) {
            throw new BizException(45203, "ANNOUNCEMENT_CONCURRENT_TRANSITION");
        }
        transitionMapper.insert(toEvidence(versionId, transition, actorId, occurredAt));
        return transition;
    }

    /**
     * 在版本发布前配置置顶窗口。
     *
     * <p>置顶时间属于收件人看到的发布版本证据，因此发布后不能原地修改；需要调整时必须创建
     * 后继版本。空的开始和结束时间表示取消尚未发布版本的置顶配置。</p>
     */
    @Transactional
    public void configurePin(String versionId, java.time.Instant pinFrom, java.time.Instant pinUntil) {
        AnnouncementVersionEntity version = requireVersion(versionId);
        AnnouncementState state = AnnouncementState.valueOf(version.getLifecycleState());
        if (state != AnnouncementState.DRAFT && state != AnnouncementState.PENDING_REVIEW
                && state != AnnouncementState.REJECTED && state != AnnouncementState.SCHEDULED) {
            throw new BizException(45204, "ANNOUNCEMENT_PUBLISHED_VERSION_IMMUTABLE");
        }
        if (pinFrom != null && pinUntil != null && !pinUntil.isAfter(pinFrom)) {
            throw new BizException(45205, "ANNOUNCEMENT_PIN_INTERVAL_INVALID");
        }
        UpdateWrapper<AnnouncementVersionEntity> update =
                new UpdateWrapper<AnnouncementVersionEntity>()
                        .eq("id", versionId)
                        .eq("tenant_id", contextService.tenantId())
                        .eq("lifecycle_state", version.getLifecycleState())
                        .set("pin_from",
                                pinFrom == null ? null : LocalDateTime.ofInstant(pinFrom, ZoneOffset.UTC))
                        .set("pin_until",
                                pinUntil == null ? null : LocalDateTime.ofInstant(pinUntil, ZoneOffset.UTC));
        if (versionMapper.update(null, update) != 1) {
            throw new BizException(45203, "ANNOUNCEMENT_CONCURRENT_TRANSITION");
        }
    }

    /** 未发布草稿可物理清理；任何已经进入发布证据链的版本都必须走撤回或替代。 */
    @Transactional
    public void deleteDraft(String versionId) {
        AnnouncementVersionEntity version = requireVersion(versionId);
        AnnouncementState state = AnnouncementState.valueOf(version.getLifecycleState());
        if (state != AnnouncementState.DRAFT && state != AnnouncementState.REJECTED) {
            throw new BizException(45204, "ANNOUNCEMENT_PUBLISHED_VERSION_IMMUTABLE");
        }
        int deleted = versionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AnnouncementVersionEntity>()
                .eq("id", versionId)
                .eq("tenant_id", contextService.tenantId())
                .in("lifecycle_state", AnnouncementState.DRAFT.name(), AnnouncementState.REJECTED.name()));
        if (deleted != 1) {
            throw new BizException(45203, "ANNOUNCEMENT_CONCURRENT_TRANSITION");
        }
    }

    private AnnouncementVersionEntity requireVersion(String versionId) {
        AnnouncementVersionEntity version = versionMapper.selectById(versionId);
        if (version == null || !contextService.tenantId().equals(version.getTenantId())) {
            throw new BizException(45201, "ANNOUNCEMENT_VERSION_NOT_FOUND");
        }
        return version;
    }

    private AnnouncementTransitionEntity toEvidence(String versionId,
                                                    AnnouncementTransition transition,
                                                    String actorId,
                                                    LocalDateTime occurredAt) {
        AnnouncementTransitionEntity evidence = new AnnouncementTransitionEntity();
        evidence.setId(UlidGenerator.nextUlid());
        evidence.setTenantId(contextService.tenantId());
        evidence.setVersionId(versionId);
        evidence.setFromState(transition.fromState().name());
        evidence.setToState(transition.toState().name());
        evidence.setTransitionType(transition.command().name());
        evidence.setReason(transition.reason());
        evidence.setActorId(actorId);
        evidence.setActorName(contextService.username());
        evidence.setTraceId(TraceUtil.getTraceId());
        evidence.setOccurredAt(occurredAt);
        return evidence;
    }
}
