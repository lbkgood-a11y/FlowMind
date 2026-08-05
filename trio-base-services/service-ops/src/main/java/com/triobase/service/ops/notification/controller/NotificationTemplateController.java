package com.triobase.service.ops.notification.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.ops.notification.dto.NotificationTemplateDraftRequest;
import com.triobase.service.ops.notification.dto.NotificationTemplatePreview;
import com.triobase.service.ops.notification.dto.NotificationTemplateView;
import com.triobase.service.ops.notification.service.NotificationTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;
import java.util.List;

/** 模板管理接口；租户身份只取认证上下文，已发布内容没有更新端点。 */
@RestController
@RequestMapping("/api/v2/notification-templates")
@RequiredArgsConstructor
public class NotificationTemplateController {
    private final NotificationTemplateService service;

    @GetMapping
    @RequirePermission("/api/v2/notification-channels:GET")
    public R<List<NotificationTemplateView>> list() {
        return R.ok(service.listCurrentTenant());
    }

    @PostMapping
    @RequirePermission("/api/v2/notification-templates/**:PUT")
    public R<NotificationTemplateView> create(@Valid @RequestBody NotificationTemplateDraftRequest request) {
        return R.ok(service.createDraft(request));
    }

    @PostMapping("/{versionId}/preview")
    @RequirePermission("/api/v2/notification-templates/**:PUT")
    public R<NotificationTemplatePreview> preview(@PathVariable String versionId,
                                                  @RequestBody Map<String, Object> variables) {
        return R.ok(service.preview(versionId, variables));
    }

    @PutMapping("/{versionId}/submit-review")
    @RequirePermission("/api/v2/notification-templates/**:PUT")
    public R<Void> submitReview(@PathVariable String versionId) {
        service.submitReview(versionId);
        return R.ok();
    }

    @PutMapping("/{versionId}/reject")
    @RequirePermission("/api/v2/notification-templates/**:PUT")
    public R<Void> reject(@PathVariable String versionId) {
        service.reject(versionId);
        return R.ok();
    }

    @PutMapping("/{versionId}/publish")
    @RequirePermission("/api/v2/notification-templates/**:PUT")
    public R<Void> publish(@PathVariable String versionId) {
        service.publish(versionId);
        return R.ok();
    }
}
