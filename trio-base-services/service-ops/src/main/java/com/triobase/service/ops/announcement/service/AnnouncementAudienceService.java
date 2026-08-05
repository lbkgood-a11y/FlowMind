package com.triobase.service.ops.announcement.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.dto.notification.AudienceSelector;
import com.triobase.common.dto.notification.AuthorizedAudienceResolver;
import com.triobase.service.ops.announcement.domain.AnnouncementState;
import com.triobase.service.ops.announcement.entity.AnnouncementAudienceRuleEntity;
import com.triobase.service.ops.announcement.entity.AnnouncementVersionEntity;
import com.triobase.service.ops.announcement.mapper.AnnouncementAudienceRuleMapper;
import com.triobase.service.ops.announcement.mapper.AnnouncementVersionMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 管理公告版本的结构化受众规则，禁止 CSV ID 和跨库成员查询。 */
@Service
@RequiredArgsConstructor
public class AnnouncementAudienceService {

    private final AnnouncementVersionMapper versionMapper;
    private final AnnouncementAudienceRuleMapper ruleMapper;
    private final AuthorizedAudienceResolverRegistry resolverRegistry;
    private final RequestContextService contextService;

    @Transactional
    public void replaceRules(String versionId, List<AudienceSelector> selectors) {
        AnnouncementVersionEntity version = requireEditableVersion(versionId);
        if (selectors == null || selectors.isEmpty()) {
            throw new BizException(45211, "ANNOUNCEMENT_AUDIENCE_REQUIRED");
        }
        if (selectors.stream().anyMatch(it -> it.getType() == AudienceSelector.AudienceType.ALL)
                && selectors.size() != 1) {
            throw new BizException(45212, "ANNOUNCEMENT_ALL_AUDIENCE_MUST_BE_EXCLUSIVE");
        }
        List<ValidatedSelector> validated = selectors.stream().map(this::authorize).toList();

        ruleMapper.delete(new QueryWrapper<AnnouncementAudienceRuleEntity>()
                .eq("tenant_id", contextService.tenantId())
                .eq("version_id", version.getId()));
        validated.forEach(item -> persist(versionId, item));
    }

    private ValidatedSelector authorize(AudienceSelector selector) {
        if (selector == null || selector.getType() == null
                || !contextService.tenantId().equals(selector.getScopeTenantId())) {
            throw new BizException(45213, "ANNOUNCEMENT_AUDIENCE_SCOPE_FORBIDDEN");
        }
        if (selector.getType() == AudienceSelector.AudienceType.ALL) {
            return new ValidatedSelector(selector, null);
        }
        if (selector.getSubjectIds() == null || selector.getSubjectIds().isEmpty()
                || selector.getSubjectIds().stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new BizException(45214, "ANNOUNCEMENT_AUDIENCE_SUBJECT_REQUIRED");
        }
        String resolverKey = resolverKey(selector);
        AuthorizedAudienceResolver resolver = resolverRegistry.require(resolverKey);
        // 解析一页的目的不是固化受众，而是让 Owner 在保存规则前验证租户和操作者数据范围。
        resolver.resolve(contextService.tenantId(), contextService.userId(), selector, null, 1);
        return new ValidatedSelector(selector, resolver);
    }

    private String resolverKey(AudienceSelector selector) {
        return switch (selector.getType()) {
            case ORGANIZATION -> "organization-members";
            case ROLE -> "role-members";
            case USER -> "authorized-users";
            case DYNAMIC_PARTICIPANT -> selector.getResolverKey();
            case ALL -> null;
        };
    }

    private void persist(String versionId, ValidatedSelector item) {
        AudienceSelector selector = item.selector();
        if (selector.getType() == AudienceSelector.AudienceType.ALL) {
            ruleMapper.insert(entity(versionId, selector, null, null, null));
            return;
        }
        selector.getSubjectIds().stream().distinct().forEach(subjectId -> ruleMapper.insert(entity(
                versionId, selector, subjectId, item.resolver().resolverKey(), item.resolver().resolverVersion())));
    }

    private AnnouncementAudienceRuleEntity entity(String versionId,
                                                   AudienceSelector selector,
                                                   String subjectId,
                                                   String resolverKey,
                                                   String resolverVersion) {
        AnnouncementAudienceRuleEntity entity = new AnnouncementAudienceRuleEntity();
        entity.setId(UlidGenerator.nextUlid());
        entity.setTenantId(contextService.tenantId());
        entity.setVersionId(versionId);
        entity.setSelectorType(selector.getType().name());
        entity.setSubjectId(subjectId);
        entity.setIncludeDescendants((short) (selector.isIncludeDescendants() ? 1 : 0));
        entity.setResolverKey(resolverKey);
        entity.setResolverVersion(resolverVersion);
        entity.setCreatedBy(contextService.userId());
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private AnnouncementVersionEntity requireEditableVersion(String versionId) {
        AnnouncementVersionEntity version = versionMapper.selectById(versionId);
        if (version == null || !contextService.tenantId().equals(version.getTenantId())) {
            throw new BizException(45201, "ANNOUNCEMENT_VERSION_NOT_FOUND");
        }
        AnnouncementState state = AnnouncementState.valueOf(version.getLifecycleState());
        if (state != AnnouncementState.DRAFT && state != AnnouncementState.REJECTED) {
            throw new BizException(45204, "ANNOUNCEMENT_PUBLISHED_VERSION_IMMUTABLE");
        }
        return version;
    }

    private record ValidatedSelector(AudienceSelector selector, AuthorizedAudienceResolver resolver) {
    }
}
