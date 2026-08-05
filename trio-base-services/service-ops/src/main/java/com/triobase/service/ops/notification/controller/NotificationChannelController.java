package com.triobase.service.ops.notification.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.ops.notification.entity.NotificationChannelEntity;
import com.triobase.service.ops.notification.dto.NotificationProviderRequest;
import com.triobase.service.ops.notification.dto.NotificationProviderView;
import com.triobase.service.ops.notification.dto.ChannelValidationView;
import com.triobase.service.ops.notification.dto.NotificationRoutingPlan;
import com.triobase.service.ops.notification.dto.NotificationRoutingPolicyRequest;
import com.triobase.service.ops.notification.dto.NotificationUserPreferenceRequest;
import com.triobase.service.ops.notification.entity.NotificationUserPreferenceEntity;
import com.triobase.service.ops.notification.entity.NotificationRoutingPolicyEntity;
import com.triobase.service.ops.notification.service.NotificationChannelCatalogService;
import com.triobase.service.ops.notification.service.NotificationProviderService;
import com.triobase.service.ops.notification.service.NotificationChannelCapabilityService;
import com.triobase.service.ops.notification.service.NotificationRoutingService;
import com.triobase.service.ops.notification.service.NotificationUserPreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 返回认证租户的渠道能力目录，不接受客户端指定 tenantId。 */
@RestController
@RequestMapping("/api/v2/notification-channels")
@RequiredArgsConstructor
public class NotificationChannelController {

    private final NotificationChannelCatalogService catalogService;
    private final NotificationProviderService providerService;
    private final NotificationChannelCapabilityService capabilityService;
    private final NotificationRoutingService routingService;
    private final NotificationUserPreferenceService preferenceService;

    @GetMapping
    @RequirePermission("/api/v2/notification-channels:GET")
    public R<List<NotificationChannelEntity>> list() {
        return R.ok(catalogService.listCurrentTenant());
    }

    @GetMapping("/providers")
    @RequirePermission("/api/v2/notification-channels:GET")
    public R<List<NotificationProviderView>> providers() {
        return R.ok(providerService.listCurrentTenant());
    }

    @PutMapping("/providers")
    @RequirePermission("/api/v2/notification-channels/**:PUT")
    public R<NotificationProviderView> saveProvider(@Valid @RequestBody NotificationProviderRequest request) {
        return R.ok(providerService.save(request));
    }

    @PutMapping("/{channelCode}/validate")
    @RequirePermission("/api/v2/notification-channels/**:PUT")
    public R<ChannelValidationView> validate(@PathVariable String channelCode,
                                             @RequestParam(required = false) String providerKey) {
        return R.ok(capabilityService.validate(channelCode, providerKey));
    }

    @PutMapping("/{channelCode}/enabled")
    @RequirePermission("/api/v2/notification-channels/**:PUT")
    public R<Void> enabled(@PathVariable String channelCode, @RequestParam boolean enabled) {
        capabilityService.setEnabled(channelCode, enabled);
        return R.ok();
    }

    @PutMapping("/routing-policies")
    @RequirePermission("/api/v2/notification-channels/**:PUT")
    public R<Void> saveRouting(@Valid @RequestBody NotificationRoutingPolicyRequest request) {
        routingService.save(request);
        return R.ok();
    }

    @GetMapping("/routing-policies")
    @RequirePermission("/api/v2/notification-channels:GET")
    public R<List<NotificationRoutingPolicyEntity>> routingPolicies() {
        return R.ok(routingService.listCurrentTenant());
    }

    @GetMapping("/routing-plan")
    @RequirePermission("/api/v2/notification-channels:GET")
    public R<NotificationRoutingPlan> routingPlan(@RequestParam String categoryCode,
                                                  @RequestParam String priorityCode) {
        return R.ok(routingService.evaluate(categoryCode, priorityCode, java.time.Instant.now()));
    }

    @GetMapping("/preferences")
    @RequirePermission("/api/v2/notification-channels:GET")
    public R<List<NotificationUserPreferenceEntity>> preferences() {
        return R.ok(preferenceService.listCurrentUser());
    }

    @PutMapping("/preferences")
    @RequirePermission("/api/v2/notification-channels:GET")
    public R<Void> savePreference(@Valid @RequestBody NotificationUserPreferenceRequest request) {
        preferenceService.save(request);
        return R.ok();
    }
}
