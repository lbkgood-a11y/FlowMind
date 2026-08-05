package com.triobase.common.dto.notification;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 跨服务通知受众选择器。
 *
 * <p>选择器只表达受众意图，不授予数据权限。通知 Owner 必须使用调用租户下已注册、
 * 已经过权限过滤的解析器展开用户；解析失败必须默认拒绝，禁止退化为全租户用户。</p>
 */
@Data
public class AudienceSelector {
    private AudienceType type;
    private String scopeTenantId;
    private List<String> subjectIds = new ArrayList<>();
    private boolean includeDescendants;
    private String resolverKey;
    private String resolverVersion;
    private boolean freezeRequired;

    public enum AudienceType {
        ALL,
        ORGANIZATION,
        ROLE,
        USER,
        DYNAMIC_PARTICIPANT
    }
}
