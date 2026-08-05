package com.triobase.service.ops.announcement.service;

import com.triobase.service.ops.announcement.entity.AnnouncementVersionEntity;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 查询 Owner 已过滤成员上下文下的可见公告，不在 JVM 中扫描全租户公告。 */
@Service
@RequiredArgsConstructor
public class AnnouncementVisibilityService {

    private final AnnouncementVisibilityMapper visibilityMapper;
    private final RequestContextService contextService;

    public List<AnnouncementVersionEntity> visible(List<String> authorizedOrgIds,
                                                   List<String> authorizedRoleIds,
                                                   LocalDateTime now,
                                                   int page,
                                                   int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        long offset = (long) Math.max(page - 1, 0) * safeSize;
        return visibilityMapper.visible(contextService.tenantId(), contextService.userId(),
                safe(authorizedOrgIds), safe(authorizedRoleIds), now, safeSize, offset);
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values.stream().filter(it -> it != null && !it.isBlank()).distinct().toList();
    }
}
