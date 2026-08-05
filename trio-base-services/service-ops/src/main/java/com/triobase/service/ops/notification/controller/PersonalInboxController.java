package com.triobase.service.ops.notification.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.ops.notification.dto.InboxBellPreview;
import com.triobase.service.ops.notification.dto.InboxBoundary;
import com.triobase.service.ops.notification.dto.InboxPage;
import com.triobase.service.ops.notification.dto.InboxQuery;
import com.triobase.service.ops.notification.service.PersonalInboxService;
import com.triobase.service.ops.notification.service.UnifiedInboxQueryService;
import com.triobase.service.ops.notification.service.InboxResourceActionService;
import com.triobase.service.ops.notification.service.InboxOwnerIntegration;
import com.triobase.common.action.model.GlobalActionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.triobase.service.ops.notification.service.InboxSseBroker;
import com.triobase.service.ops.notification.service.NotificationCutoverService;
import com.triobase.service.ops.service.RequestContextService;

import java.util.List;

/** 当前认证用户的个人消息状态接口；请求体不接受 tenantId 或 userId。 */
@RestController
@RequestMapping("/api/v2/inbox")
@RequiredArgsConstructor
public class PersonalInboxController {

    private final PersonalInboxService service;
    private final UnifiedInboxQueryService queryService;
    private final InboxResourceActionService resourceActionService;
    private final InboxSseBroker sseBroker;
    private final RequestContextService contextService;
    private final NotificationCutoverService cutoverService;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission("/api/v2/inbox:GET")
    public SseEmitter events() {
        requireV2Read();
        return sseBroker.connect(contextService.tenantId(), contextService.userId());
    }

    @GetMapping
    @RequirePermission("/api/v2/inbox:GET")
    public R<InboxPage> page(@RequestParam(required = false) String itemType,
                             @RequestParam(required = false) String readState,
                             @RequestParam(required = false) String sourceOwner,
                             @RequestParam(required = false)
                             @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
                             java.time.LocalDateTime from,
                             @RequestParam(required = false)
                             @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
                             java.time.LocalDateTime to,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "20") int size) {
        requireV2Read();
        return R.ok(queryService.find(new InboxQuery(
                itemType, readState, sourceOwner, from, to, page, size)));
    }

    @GetMapping("/bell")
    @RequirePermission("/api/v2/inbox:GET")
    public R<InboxBellPreview> bell(@RequestParam(defaultValue = "10") int limit) {
        requireV2Read();
        return R.ok(service.bell(limit));
    }

    @PostMapping("/read")
    @RequirePermission("/api/v2/inbox:GET")
    public R<Void> markRead(@RequestBody IdsRequest request) {
        requireV2Read();
        service.markRead(request.ids());
        return R.ok();
    }

    @PostMapping("/read-all")
    @RequirePermission("/api/v2/inbox:GET")
    public R<Void> markAllRead(@RequestBody InboxBoundary boundary) {
        requireV2Read();
        service.markAllRead(boundary);
        return R.ok();
    }

    @PostMapping("/{id}/archive")
    @RequirePermission("/api/v2/inbox:GET")
    public R<Void> archive(@PathVariable String id) {
        requireV2Read();
        service.archive(id);
        return R.ok();
    }

    @PostMapping("/{id}/restore")
    @RequirePermission("/api/v2/inbox:GET")
    public R<Void> restore(@PathVariable String id) {
        requireV2Read();
        service.restore(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequirePermission("/api/v2/inbox:GET")
    public R<Void> hide(@PathVariable String id) {
        requireV2Read();
        service.hide(id);
        return R.ok();
    }

    @GetMapping("/{id}/navigation")
    @RequirePermission("/api/v2/inbox:GET")
    public R<InboxOwnerIntegration.RegisteredNavigation> navigation(@PathVariable String id) {
        requireV2Read();
        return R.ok(resourceActionService.navigation(id));
    }

    @PostMapping("/{id}/actions/execute")
    @RequirePermission("/api/v2/inbox:GET")
    public R<GlobalActionResult> execute(@PathVariable String id,
                                         @RequestBody ActionRequest request) {
        requireV2Read();
        return R.ok(resourceActionService.execute(id, request.idempotencyKey(), request.payload()));
    }

    private void requireV2Read() {
        cutoverService.requireV2Read(contextService.tenantId());
    }

    public record IdsRequest(List<String> ids) {
    }

    public record ActionRequest(String idempotencyKey, java.util.Map<String, Object> payload) {
    }
}
