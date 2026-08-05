package com.triobase.service.ops.announcement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.dto.notification.AudienceSelector;
import com.triobase.service.ops.announcement.dto.AnnouncementWorkbenchRequest;
import com.triobase.service.ops.announcement.entity.AnnouncementIdentityEntity;
import com.triobase.service.ops.announcement.entity.AnnouncementVersionEntity;
import com.triobase.service.ops.announcement.mapper.AnnouncementIdentityMapper;
import com.triobase.service.ops.announcement.mapper.AnnouncementVersionMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

/** 创建公告身份和不可变版本，并将类型化受众保存委托给授权解析边界。 */
@Service
@RequiredArgsConstructor
public class AnnouncementWorkbenchService {
    private final AnnouncementIdentityMapper identityMapper;
    private final AnnouncementVersionMapper versionMapper;
    private final AnnouncementAudienceService audienceService;
    private final RequestContextService contextService;

    public PageResult<AnnouncementVersionEntity> page(int page, int size, String keyword, String state) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        IPage<AnnouncementVersionEntity> result = versionMapper.selectPage(new Page<>(Math.max(page, 1), safeSize),
                new LambdaQueryWrapper<AnnouncementVersionEntity>()
                        .eq(AnnouncementVersionEntity::getTenantId, contextService.tenantId())
                        .and(StringUtils.hasText(keyword), query -> query
                                .like(AnnouncementVersionEntity::getTitle, keyword)
                                .or().like(AnnouncementVersionEntity::getContent, keyword))
                        .eq(StringUtils.hasText(state), AnnouncementVersionEntity::getLifecycleState, state)
                        .orderByDesc(AnnouncementVersionEntity::getCreatedAt));
        return PageResult.of(result.getRecords(), result.getTotal(), Math.max(page, 1), safeSize);
    }

    @Transactional
    public AnnouncementVersionEntity create(AnnouncementWorkbenchRequest request) {
        String identityId = UlidGenerator.nextUlid();
        AnnouncementIdentityEntity identity = new AnnouncementIdentityEntity();
        identity.setId(identityId);
        identity.setTenantId(contextService.tenantId());
        identity.setAnnouncementCode("ANN-" + identityId);
        identity.setCreatedBy(contextService.userId());
        identityMapper.insert(identity);

        AnnouncementVersionEntity version = buildVersion(identityId, 1, null, request);
        versionMapper.insert(version);
        audienceService.replaceRules(version.getId(), request.audience().stream().map(this::selector).toList());
        identity.setCurrentVersionId(version.getId());
        identity.setUpdatedBy(contextService.userId());
        identityMapper.updateById(identity);
        return version;
    }

    @Transactional
    public AnnouncementVersionEntity createNextVersion(String predecessorId, AnnouncementWorkbenchRequest request) {
        AnnouncementVersionEntity predecessor = requireVersion(predecessorId);
        Integer maxVersion = versionMapper.selectList(new LambdaQueryWrapper<AnnouncementVersionEntity>()
                        .eq(AnnouncementVersionEntity::getTenantId, contextService.tenantId())
                        .eq(AnnouncementVersionEntity::getAnnouncementId, predecessor.getAnnouncementId()))
                .stream().map(AnnouncementVersionEntity::getVersionNo).max(Integer::compareTo).orElse(0);
        AnnouncementVersionEntity version = buildVersion(
                predecessor.getAnnouncementId(), maxVersion + 1, predecessorId, request);
        versionMapper.insert(version);
        audienceService.replaceRules(version.getId(), request.audience().stream().map(this::selector).toList());
        AnnouncementIdentityEntity identity = identityMapper.selectById(predecessor.getAnnouncementId());
        identity.setCurrentVersionId(version.getId());
        identity.setUpdatedBy(contextService.userId());
        identityMapper.updateById(identity);
        return version;
    }

    private AnnouncementVersionEntity buildVersion(String announcementId, int number, String predecessorId,
                                                    AnnouncementWorkbenchRequest request) {
        if (request.confirmationRequired() && !StringUtils.hasText(request.confirmationStatement())) {
            throw new BizException(45215, "ANNOUNCEMENT_CONFIRMATION_STATEMENT_REQUIRED");
        }
        if (request.pinFrom() != null && request.pinUntil() != null
                && !request.pinUntil().isAfter(request.pinFrom())) {
            throw new BizException(45205, "ANNOUNCEMENT_PIN_INTERVAL_INVALID");
        }
        AnnouncementVersionEntity version = new AnnouncementVersionEntity();
        version.setId(UlidGenerator.nextUlid());
        version.setTenantId(contextService.tenantId());
        version.setAnnouncementId(announcementId);
        version.setVersionNo(number);
        version.setTitle(request.title().trim());
        version.setContent(request.content());
        version.setPriority(StringUtils.hasText(request.priority()) ? request.priority() : "NORMAL");
        version.setLifecycleState("DRAFT");
        version.setAudienceMode(request.confirmationRequired() ? "FROZEN" : "DYNAMIC");
        version.setConfirmationRequired((short) (request.confirmationRequired() ? 1 : 0));
        version.setConfirmationStatement(request.confirmationStatement());
        version.setConfirmationStatementHash(hash(request.confirmationStatement()));
        version.setConfirmationDeadline(request.confirmationDeadline());
        version.setEffectiveUntil(request.effectiveUntil());
        version.setPinFrom(request.pinFrom());
        version.setPinUntil(request.pinUntil());
        version.setPredecessorVersionId(predecessorId);
        version.setCreatedBy(contextService.userId());
        return version;
    }

    private AnnouncementVersionEntity requireVersion(String id) {
        AnnouncementVersionEntity version = versionMapper.selectById(id);
        if (version == null || !contextService.tenantId().equals(version.getTenantId())) {
            throw new BizException(45201, "ANNOUNCEMENT_VERSION_NOT_FOUND");
        }
        return version;
    }

    private AudienceSelector selector(AnnouncementWorkbenchRequest.AudienceRule rule) {
        AudienceSelector selector = new AudienceSelector();
        selector.setType(AudienceSelector.AudienceType.valueOf(rule.type().name()));
        selector.setScopeTenantId(contextService.tenantId());
        selector.setSubjectIds(rule.subjectIds() == null ? java.util.List.of() : rule.subjectIds());
        selector.setIncludeDescendants(rule.includeDescendants());
        selector.setResolverKey(rule.resolverKey());
        selector.setFreezeRequired(false);
        return selector;
    }

    private String hash(String statement) {
        if (!StringUtils.hasText(statement)) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(statement.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
