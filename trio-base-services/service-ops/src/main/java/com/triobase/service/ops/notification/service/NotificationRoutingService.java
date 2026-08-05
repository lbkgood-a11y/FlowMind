package com.triobase.service.ops.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.ops.notification.dto.NotificationRoutingPlan;
import com.triobase.service.ops.notification.dto.NotificationRoutingPolicyRequest;
import com.triobase.service.ops.notification.entity.NotificationChannelEntity;
import com.triobase.service.ops.notification.entity.NotificationRoutingPolicyEntity;
import com.triobase.service.ops.notification.mapper.NotificationChannelMapper;
import com.triobase.service.ops.notification.mapper.NotificationRoutingPolicyMapper;
import com.triobase.service.ops.notification.mapper.NotificationUserPreferenceMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/** 按租户策略生成可解释的渠道计划，首期外部渠道统一产生 SKIPPED_UNAVAILABLE。 */
@Service
@RequiredArgsConstructor
public class NotificationRoutingService {
    private final NotificationRoutingPolicyMapper policyMapper;
    private final NotificationChannelMapper channelMapper;
    private final RequestContextService contextService;
    private final ObjectMapper objectMapper;
    private final NotificationUserPreferenceMapper preferenceMapper;
    private final NotificationConfigurationAuditService auditService;

    public List<NotificationRoutingPolicyEntity> listCurrentTenant() {
        return policyMapper.selectList(new LambdaQueryWrapper<NotificationRoutingPolicyEntity>()
                .eq(NotificationRoutingPolicyEntity::getTenantId, contextService.tenantId())
                .orderByAsc(NotificationRoutingPolicyEntity::getCategoryCode)
                .orderByAsc(NotificationRoutingPolicyEntity::getPriorityCode));
    }

    @Transactional
    public void save(NotificationRoutingPolicyRequest request) {
        if (new HashSet<>(request.orderedChannels()).size() != request.orderedChannels().size()) {
            throw new BizException(45520, "ROUTING_CHANNEL_DUPLICATED");
        }
        validateQuietHours(request.quietHours());
        String tenantId = contextService.tenantId();
        NotificationRoutingPolicyEntity existing = policyMapper.findOwned(
                tenantId, request.categoryCode(), request.priorityCode());
        String channels = String.join(",", request.orderedChannels());
        String quietHours = write(request.quietHours());
        if (existing == null) {
            existing = new NotificationRoutingPolicyEntity();
            existing.setId(UUID.randomUUID().toString().replace("-", ""));
            existing.setTenantId(tenantId);
            existing.setCategoryCode(request.categoryCode());
            existing.setPriorityCode(request.priorityCode());
            existing.setOrderedChannels(channels);
            existing.setFallbackEnabled(request.fallbackEnabled() ? 1 : 0);
            existing.setQuietHoursJson(quietHours);
            existing.setMandatoryCategory(request.mandatoryCategory() ? 1 : 0);
            existing.setEnabled(1);
            policyMapper.insert(existing);
        } else {
            policyMapper.updateOwned(tenantId, request.categoryCode(), request.priorityCode(), channels,
                    request.fallbackEnabled() ? 1 : 0, quietHours, request.mandatoryCategory() ? 1 : 0);
        }
        auditService.record("ROUTING_POLICY", request.categoryCode() + ":" + request.priorityCode(),
                "SAVED", request.mandatoryCategory() ? "MANDATORY" : "OPTIONAL");
    }

    public NotificationRoutingPlan evaluate(String categoryCode, String priorityCode, Instant now) {
        String tenantId = contextService.tenantId();
        NotificationRoutingPolicyEntity policy = policyMapper.findEffective(tenantId, categoryCode, priorityCode);
        List<String> ordered = policy == null ? List.of("IN_APP")
                : List.of(policy.getOrderedChannels().split(","));
        boolean fallback = policy != null && Integer.valueOf(1).equals(policy.getFallbackEnabled());
        boolean mandatory = policy != null && Integer.valueOf(1).equals(policy.getMandatoryCategory());
        boolean quiet = policy != null && inQuietHours(policy.getQuietHoursJson(), now);
        List<NotificationRoutingPlan.ChannelDecision> decisions = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            String channelCode = ordered.get(index);
            NotificationChannelEntity channel = channelMapper.findOwned(tenantId, channelCode);
            var preference = preferenceMapper.findOwned(tenantId, contextService.userId(), categoryCode, channelCode);
            if (index > 0 && !fallback) {
                decisions.add(decision(channelCode, "SKIPPED_POLICY", "FALLBACK_DISABLED"));
            } else if (channel == null || !"READY".equals(channel.getCapabilityState())
                    || !Integer.valueOf(1).equals(channel.getDesiredEnabled())) {
                decisions.add(decision(channelCode, "SKIPPED_UNAVAILABLE", "CHANNEL_NOT_READY"));
            } else if (!mandatory && preference != null && Integer.valueOf(0).equals(preference.getEnabled())) {
                decisions.add(decision(channelCode, "SKIPPED_PREFERENCE", "USER_OPT_OUT"));
            } else if (quiet && !mandatory && !"URGENT".equals(priorityCode)) {
                decisions.add(decision(channelCode, "DEFERRED_QUIET_HOURS", "TENANT_QUIET_HOURS"));
            } else {
                decisions.add(decision(channelCode, "ELIGIBLE", "READY"));
            }
        }
        return new NotificationRoutingPlan(categoryCode, priorityCode, mandatory, List.copyOf(decisions));
    }

    private NotificationRoutingPlan.ChannelDecision decision(String channel, String value, String reason) {
        return new NotificationRoutingPlan.ChannelDecision(channel, value, reason);
    }

    private boolean inQuietHours(String json, Instant now) {
        if (json == null || json.isBlank()) return false;
        try {
            NotificationRoutingPolicyRequest.QuietHours quiet = objectMapper.readValue(
                    json, NotificationRoutingPolicyRequest.QuietHours.class);
            LocalTime current = now.atZone(ZoneId.of(quiet.zoneId())).toLocalTime();
            LocalTime start = LocalTime.parse(quiet.start());
            LocalTime end = LocalTime.parse(quiet.end());
            return start.equals(end) || start.isBefore(end)
                    ? !current.isBefore(start) && current.isBefore(end)
                    : !current.isBefore(start) || current.isBefore(end);
        } catch (JsonProcessingException | ZoneRulesException | java.time.format.DateTimeParseException error) {
            throw new BizException(45521, "QUIET_HOURS_INVALID");
        }
    }

    private void validateQuietHours(NotificationRoutingPolicyRequest.QuietHours quiet) {
        if (quiet == null) return;
        try {
            LocalTime.parse(quiet.start());
            LocalTime.parse(quiet.end());
            ZoneId.of(quiet.zoneId());
        } catch (RuntimeException error) {
            throw new BizException(45521, "QUIET_HOURS_INVALID");
        }
    }

    private String write(NotificationRoutingPolicyRequest.QuietHours quiet) {
        if (quiet == null) return null;
        try {
            return objectMapper.writeValueAsString(quiet);
        } catch (JsonProcessingException error) {
            throw new BizException(45521, "QUIET_HOURS_INVALID");
        }
    }
}
