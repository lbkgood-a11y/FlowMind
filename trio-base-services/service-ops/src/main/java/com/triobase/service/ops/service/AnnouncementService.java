package com.triobase.service.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.ops.dto.SaveAnnouncementRequest;
import com.triobase.service.ops.entity.OpsAnnouncement;
import com.triobase.service.ops.entity.OpsAnnouncementRead;
import com.triobase.service.ops.mapper.AnnouncementMapper;
import com.triobase.service.ops.mapper.AnnouncementReadMapper;
import com.triobase.service.ops.notification.service.LegacyNotificationDualWriteService;
import com.triobase.service.ops.notification.service.NotificationCutoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 维护兼容窗口内的旧版公告事实，并按租户开关同步到新版公告投影。
 *
 * <p>旧写入仍由本服务拥有；双写失败必须回滚同一事务，避免切换新版读取后出现不可解释的
 * 公告缺口。已发布公告的新版治理操作由公告聚合服务负责。</p>
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_OFFLINE = "OFFLINE";
    private static final String TARGET_ALL = "ALL";
    private static final String TARGET_ORG = "ORG";
    private static final String TARGET_USER = "USER";

    private final AnnouncementMapper announcementMapper;
    private final AnnouncementReadMapper announcementReadMapper;
    private final RequestContextService contextService;
    private final LegacyNotificationDualWriteService dualWriteService;
    private final NotificationCutoverService cutoverService;

    public PageResult<OpsAnnouncement> page(int page,
                                            int size,
                                            String keyword,
                                            String status,
                                            String priority) {
        LambdaQueryWrapper<OpsAnnouncement> wrapper = new LambdaQueryWrapper<OpsAnnouncement>()
                .eq(OpsAnnouncement::getTenantId, contextService.tenantId())
                .and(StringUtils.hasText(keyword),
                        w -> w.like(OpsAnnouncement::getTitle, keyword)
                                .or()
                                .like(OpsAnnouncement::getContent, keyword))
                .eq(StringUtils.hasText(status), OpsAnnouncement::getStatus, status)
                .eq(StringUtils.hasText(priority), OpsAnnouncement::getPriority, priority)
                .orderByDesc(OpsAnnouncement::getUpdatedAt);
        IPage<OpsAnnouncement> result = announcementMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    public OpsAnnouncement detail(String id) {
        return requireAnnouncement(id);
    }

    @Transactional
    public OpsAnnouncement create(SaveAnnouncementRequest request) {
        requireLegacyWrite();
        OpsAnnouncement announcement = new OpsAnnouncement();
        announcement.setId(UlidGenerator.nextUlid());
        announcement.setTenantId(contextService.tenantId());
        applyRequest(announcement, request);
        announcement.setStatus(STATUS_DRAFT);
        announcementMapper.insert(announcement);
        dualWriteService.announcementChanged(announcement.getTenantId(), announcement.getId(), true);
        return announcement;
    }

    @Transactional
    public OpsAnnouncement update(String id, SaveAnnouncementRequest request) {
        requireLegacyWrite();
        OpsAnnouncement announcement = requireAnnouncement(id);
        applyRequest(announcement, request);
        announcementMapper.updateById(announcement);
        dualWriteService.announcementChanged(announcement.getTenantId(), announcement.getId(), true);
        return announcement;
    }

    @Transactional
    public void delete(String id) {
        requireLegacyWrite();
        OpsAnnouncement announcement = requireAnnouncement(id);
        // 已发布内容是阅读与审计证据，管理端只能下线/撤回；物理删除仅保留给未发布草稿。
        if (!STATUS_DRAFT.equals(announcement.getStatus())) {
            throw new BizException(45003, "ANNOUNCEMENT_PUBLISHED_DELETE_FORBIDDEN");
        }
        announcementMapper.deleteById(id);
    }

    @Transactional
    public OpsAnnouncement publish(String id) {
        requireLegacyWrite();
        OpsAnnouncement announcement = requireAnnouncement(id);
        announcement.setStatus(STATUS_PUBLISHED);
        announcement.setPublishAt(LocalDateTime.now());
        announcement.setUnpublishAt(null);
        announcementMapper.updateById(announcement);
        dualWriteService.announcementChanged(announcement.getTenantId(), announcement.getId(), false);
        return announcement;
    }

    @Transactional
    public OpsAnnouncement unpublish(String id) {
        requireLegacyWrite();
        OpsAnnouncement announcement = requireAnnouncement(id);
        announcement.setStatus(STATUS_OFFLINE);
        announcement.setUnpublishAt(LocalDateTime.now());
        announcementMapper.updateById(announcement);
        dualWriteService.announcementChanged(announcement.getTenantId(), announcement.getId(), false);
        return announcement;
    }

    public PageResult<OpsAnnouncement> visible(int page, int size, List<String> orgIds) {
        String userId = contextService.userId();
        List<OpsAnnouncement> all = announcementMapper.selectList(new LambdaQueryWrapper<OpsAnnouncement>()
                .eq(OpsAnnouncement::getTenantId, contextService.tenantId())
                .eq(OpsAnnouncement::getStatus, STATUS_PUBLISHED)
                .orderByDesc(OpsAnnouncement::getPublishAt));
        Set<String> orgSet = orgIds != null ? new HashSet<>(orgIds) : Collections.emptySet();
        List<OpsAnnouncement> visible = all.stream()
                .filter(item -> isTargetUser(item, userId, orgSet))
                .toList();
        int from = Math.min(Math.max(page - 1, 0) * size, visible.size());
        int to = Math.min(from + size, visible.size());
        return PageResult.of(visible.subList(from, to), visible.size(), page, size);
    }

    @Transactional
    public void markRead(String id) {
        requireLegacyWrite();
        OpsAnnouncement announcement = requireAnnouncement(id);
        if (!STATUS_PUBLISHED.equals(announcement.getStatus())) {
            throw new BizException(45002, "ANNOUNCEMENT_NOT_PUBLISHED");
        }
        String userId = contextService.userId();
        Long exists = announcementReadMapper.selectCount(new LambdaQueryWrapper<OpsAnnouncementRead>()
                .eq(OpsAnnouncementRead::getAnnouncementId, id)
                .eq(OpsAnnouncementRead::getUserId, userId));
        if (exists > 0) {
            return;
        }
        OpsAnnouncementRead read = new OpsAnnouncementRead();
        read.setId(UlidGenerator.nextUlid());
        read.setTenantId(contextService.tenantId());
        read.setAnnouncementId(id);
        read.setUserId(userId);
        read.setReadAt(LocalDateTime.now());
        announcementReadMapper.insert(read);
        dualWriteService.announcementRead(read.getTenantId(), read.getId());
    }

    public long unreadCount(List<String> orgIds) {
        String userId = contextService.userId();
        Set<String> orgSet = orgIds != null ? new HashSet<>(orgIds) : Collections.emptySet();
        List<OpsAnnouncement> visible = announcementMapper.selectList(new LambdaQueryWrapper<OpsAnnouncement>()
                .eq(OpsAnnouncement::getTenantId, contextService.tenantId())
                .eq(OpsAnnouncement::getStatus, STATUS_PUBLISHED));
        return visible.stream()
                .filter(item -> isTargetUser(item, userId, orgSet))
                .filter(item -> announcementReadMapper.selectCount(new LambdaQueryWrapper<OpsAnnouncementRead>()
                        .eq(OpsAnnouncementRead::getAnnouncementId, item.getId())
                        .eq(OpsAnnouncementRead::getUserId, userId)) == 0)
                .count();
    }

    private void applyRequest(OpsAnnouncement announcement, SaveAnnouncementRequest request) {
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcement.setPriority(StringUtils.hasText(request.getPriority()) ? request.getPriority() : "NORMAL");
        announcement.setTargetType(StringUtils.hasText(request.getTargetType()) ? request.getTargetType() : TARGET_ALL);
        announcement.setTargetOrgIds(request.getTargetOrgIds());
        announcement.setTargetUserIds(request.getTargetUserIds());
    }

    private OpsAnnouncement requireAnnouncement(String id) {
        OpsAnnouncement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BizException(45001, "ANNOUNCEMENT_NOT_FOUND");
        }
        return announcement;
    }

    private boolean isTargetUser(OpsAnnouncement announcement, String userId, Set<String> orgIds) {
        String targetType = announcement.getTargetType();
        if (TARGET_ALL.equals(targetType)) {
            return true;
        }
        if (TARGET_USER.equals(targetType)) {
            return split(announcement.getTargetUserIds()).contains(userId);
        }
        if (TARGET_ORG.equals(targetType)) {
            Set<String> targetOrgIds = split(announcement.getTargetOrgIds());
            return orgIds.stream().anyMatch(targetOrgIds::contains);
        }
        return false;
    }

    private Set<String> split(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList());
    }

    private void requireLegacyWrite() {
        cutoverService.requireLegacyWrite(contextService.tenantId());
    }
}
