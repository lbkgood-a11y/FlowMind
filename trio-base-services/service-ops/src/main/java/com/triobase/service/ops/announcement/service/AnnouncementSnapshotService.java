package com.triobase.service.ops.announcement.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.dto.notification.AudienceSelector;
import com.triobase.common.dto.notification.AuthorizedAudienceResolver;
import com.triobase.service.ops.announcement.entity.AnnouncementAudienceRuleEntity;
import com.triobase.service.ops.announcement.entity.AnnouncementSnapshotCheckpointEntity;
import com.triobase.service.ops.announcement.entity.AnnouncementSnapshotEntity;
import com.triobase.service.ops.announcement.entity.AnnouncementVersionEntity;
import com.triobase.service.ops.announcement.mapper.AnnouncementAudienceRuleMapper;
import com.triobase.service.ops.announcement.mapper.AnnouncementSnapshotCheckpointMapper;
import com.triobase.service.ops.announcement.mapper.AnnouncementSnapshotMapper;
import com.triobase.service.ops.announcement.mapper.AnnouncementVersionMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 以 Owner 游标分批冻结强制确认公告的受众。
 *
 * <p>每批快照和下一游标在同一事务提交；Worker 重试时读取 checkpoint，并依赖快照唯一键跳过
 * 已写用户。规则解析完成前不得把公告发布为对外可见状态。</p>
 */
@Service
@RequiredArgsConstructor
public class AnnouncementSnapshotService {

    private final AnnouncementVersionMapper versionMapper;
    private final AnnouncementAudienceRuleMapper ruleMapper;
    private final AnnouncementSnapshotMapper snapshotMapper;
    private final AnnouncementSnapshotCheckpointMapper checkpointMapper;
    private final AuthorizedAudienceResolverRegistry resolverRegistry;
    private final RequestContextService contextService;

    @Transactional
    public SnapshotBatchResult resolveNext(String versionId, String ruleId, int requestedLimit) {
        AnnouncementVersionEntity version = versionMapper.selectById(versionId);
        AnnouncementAudienceRuleEntity rule = ruleMapper.selectById(ruleId);
        requireOwnedFrozen(version, rule);
        AnnouncementSnapshotCheckpointEntity checkpoint = checkpointMapper.selectOne(
                new QueryWrapper<AnnouncementSnapshotCheckpointEntity>()
                        .eq("tenant_id", contextService.tenantId())
                        .eq("version_id", versionId)
                        .eq("rule_id", ruleId));
        if (checkpoint != null && "COMPLETED".equals(checkpoint.getCheckpointState())) {
            return new SnapshotBatchResult(0, null, true);
        }

        String resolverKey = resolverKey(rule);
        AuthorizedAudienceResolver resolver = resolverRegistry.require(resolverKey);
        AudienceSelector selector = selector(rule, version.getTenantId());
        int limit = Math.min(Math.max(requestedLimit, 1), 1000);
        String cursor = checkpoint == null ? null : checkpoint.getCursorValue();
        AuthorizedAudienceResolver.AudienceResolutionPage page = resolver.resolve(
                version.getTenantId(), contextService.userId(), selector, cursor, limit);
        List<String> users = page == null || page.authorizedUserIds() == null
                ? List.of() : page.authorizedUserIds().stream()
                        .filter(it -> it != null && !it.isBlank()).distinct().toList();
        LocalDateTime now = LocalDateTime.now();
        int inserted = 0;
        for (String userId : users) {
            inserted += snapshotMapper.insertIgnore(snapshot(
                    versionId, userId, resolver.resolverKey(), resolver.resolverVersion(), now));
        }
        boolean completed = page == null || page.nextCursor() == null || page.nextCursor().isBlank();
        checkpointMapper.upsert(checkpoint(
                versionId, ruleId, completed ? null : page.nextCursor(), completed, inserted, now));
        return new SnapshotBatchResult(inserted, completed ? null : page.nextCursor(), completed);
    }

    private void requireOwnedFrozen(AnnouncementVersionEntity version, AnnouncementAudienceRuleEntity rule) {
        String tenantId = contextService.tenantId();
        if (version == null || rule == null || !tenantId.equals(version.getTenantId())
                || !tenantId.equals(rule.getTenantId()) || !version.getId().equals(rule.getVersionId())) {
            throw new BizException(45230, "ANNOUNCEMENT_SNAPSHOT_RULE_NOT_FOUND");
        }
        if (!"FROZEN".equals(version.getAudienceMode())) {
            throw new BizException(45231, "ANNOUNCEMENT_SNAPSHOT_NOT_REQUIRED");
        }
    }

    private String resolverKey(AnnouncementAudienceRuleEntity rule) {
        return "ALL".equals(rule.getSelectorType()) ? "authorized-users" : rule.getResolverKey();
    }

    private AudienceSelector selector(AnnouncementAudienceRuleEntity rule, String tenantId) {
        AudienceSelector selector = new AudienceSelector();
        selector.setType(AudienceSelector.AudienceType.valueOf(rule.getSelectorType()));
        selector.setScopeTenantId(tenantId);
        selector.setSubjectIds(rule.getSubjectId() == null ? List.of() : List.of(rule.getSubjectId()));
        selector.setIncludeDescendants(Short.valueOf((short) 1).equals(rule.getIncludeDescendants()));
        selector.setResolverKey(rule.getResolverKey());
        selector.setResolverVersion(rule.getResolverVersion());
        selector.setFreezeRequired(true);
        return selector;
    }

    private AnnouncementSnapshotEntity snapshot(String versionId, String userId,
                                                String resolverKey, String resolverVersion,
                                                LocalDateTime now) {
        AnnouncementSnapshotEntity entity = new AnnouncementSnapshotEntity();
        entity.setId(UlidGenerator.nextUlid());
        entity.setTenantId(contextService.tenantId());
        entity.setVersionId(versionId);
        entity.setRecipientUserId(userId);
        entity.setResolverKey(resolverKey);
        entity.setResolverVersion(resolverVersion);
        entity.setResolvedAt(now);
        return entity;
    }

    private AnnouncementSnapshotCheckpointEntity checkpoint(String versionId, String ruleId,
                                                            String cursor, boolean completed,
                                                            long resolvedCount, LocalDateTime now) {
        AnnouncementSnapshotCheckpointEntity entity = new AnnouncementSnapshotCheckpointEntity();
        entity.setId(UlidGenerator.nextUlid());
        entity.setTenantId(contextService.tenantId());
        entity.setVersionId(versionId);
        entity.setRuleId(ruleId);
        entity.setCursorValue(cursor);
        entity.setCheckpointState(completed ? "COMPLETED" : "RUNNING");
        entity.setResolvedCount(resolvedCount);
        entity.setUpdatedAt(now);
        return entity;
    }

    public record SnapshotBatchResult(int insertedCount, String nextCursor, boolean completed) {
    }
}
