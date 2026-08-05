package com.triobase.service.ops.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.ops.notification.dto.NotificationUserPreferenceRequest;
import com.triobase.service.ops.notification.dto.NotificationRoutingPolicyRequest;
import com.triobase.service.ops.notification.entity.NotificationRoutingPolicyEntity;
import com.triobase.service.ops.notification.entity.NotificationUserPreferenceEntity;
import com.triobase.service.ops.notification.mapper.NotificationRoutingPolicyMapper;
import com.triobase.service.ops.notification.mapper.NotificationUserPreferenceMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** 管理当前用户的未来渠道偏好；租户强制类别始终优先于个人退订。 */
@Service
@RequiredArgsConstructor
public class NotificationUserPreferenceService {
    private final NotificationUserPreferenceMapper preferenceMapper;
    private final NotificationRoutingPolicyMapper policyMapper;
    private final RequestContextService contextService;
    private final ObjectMapper objectMapper;
    private final NotificationConfigurationAuditService auditService;

    public List<NotificationUserPreferenceEntity> listCurrentUser() {
        return preferenceMapper.findByUser(contextService.tenantId(), contextService.userId());
    }

    @Transactional
    public void save(NotificationUserPreferenceRequest request) {
        String tenantId = contextService.tenantId();
        String userId = contextService.userId();
        NotificationRoutingPolicyEntity policy = policyMapper.findEffective(
                tenantId, request.categoryCode(), "NORMAL");
        if (!request.enabled() && policy != null && Integer.valueOf(1).equals(policy.getMandatoryCategory())) {
            throw new BizException(45522, "MANDATORY_CATEGORY_OPT_OUT_FORBIDDEN");
        }
        String quiet = writeAndValidate(request.quietHours());
        NotificationUserPreferenceEntity existing = preferenceMapper.findOwned(
                tenantId, userId, request.categoryCode(), request.channelCode());
        if (existing == null) {
            existing = new NotificationUserPreferenceEntity();
            existing.setId(UUID.randomUUID().toString().replace("-", ""));
            existing.setTenantId(tenantId);
            existing.setUserId(userId);
            existing.setCategoryCode(request.categoryCode());
            existing.setChannelCode(request.channelCode());
            existing.setEnabled(request.enabled() ? 1 : 0);
            existing.setQuietHoursJson(quiet);
            preferenceMapper.insert(existing);
        } else {
            preferenceMapper.updateOwned(tenantId, userId, request.categoryCode(), request.channelCode(),
                    request.enabled() ? 1 : 0, quiet);
        }
        auditService.record("USER_PREFERENCE", request.categoryCode() + ":" + request.channelCode(),
                "SAVED", request.enabled() ? "ENABLED" : "DISABLED");
    }

    private String writeAndValidate(NotificationRoutingPolicyRequest.QuietHours quiet) {
        if (quiet == null) return null;
        try {
            LocalTime.parse(quiet.start());
            LocalTime.parse(quiet.end());
            ZoneId.of(quiet.zoneId());
            return objectMapper.writeValueAsString(quiet);
        } catch (RuntimeException | JsonProcessingException error) {
            throw new BizException(45521, "QUIET_HOURS_INVALID");
        }
    }
}
