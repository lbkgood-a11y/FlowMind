package com.triobase.service.ops.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.ops.dto.SaveAnnouncementRequest;
import com.triobase.service.ops.entity.OpsAnnouncement;
import com.triobase.service.ops.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    @RequirePermission("/api/v1/announcements:GET")
    public R<PageResult<OpsAnnouncement>> page(@RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String priority) {
        return R.ok(announcementService.page(page, size, keyword, status, priority));
    }

    @GetMapping("/{id}")
    @RequirePermission("/api/v1/announcements:GET")
    public R<OpsAnnouncement> detail(@PathVariable String id) {
        return R.ok(announcementService.detail(id));
    }

    @PostMapping
    @RequirePermission("/api/v1/announcements:POST")
    public R<OpsAnnouncement> create(@Valid @RequestBody SaveAnnouncementRequest request) {
        return R.ok(announcementService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("/api/v1/announcements/*:PUT")
    public R<OpsAnnouncement> update(@PathVariable String id,
                                     @Valid @RequestBody SaveAnnouncementRequest request) {
        return R.ok(announcementService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("/api/v1/announcements/*:DELETE")
    public R<Void> delete(@PathVariable String id) {
        announcementService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/publish")
    @RequirePermission("/api/v1/announcements/*/publish:POST")
    public R<OpsAnnouncement> publish(@PathVariable String id) {
        return R.ok(announcementService.publish(id));
    }

    @PostMapping("/{id}/unpublish")
    @RequirePermission("/api/v1/announcements/*/unpublish:POST")
    public R<OpsAnnouncement> unpublish(@PathVariable String id) {
        return R.ok(announcementService.unpublish(id));
    }

    @GetMapping("/visible")
    public R<PageResult<OpsAnnouncement>> visible(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) List<String> orgIds) {
        return R.ok(announcementService.visible(page, size, orgIds));
    }

    @PostMapping("/{id}/read")
    public R<Void> markRead(@PathVariable String id) {
        announcementService.markRead(id);
        return R.ok();
    }

    @GetMapping("/unread-count")
    public R<Long> unreadCount(@RequestParam(required = false) List<String> orgIds) {
        return R.ok(announcementService.unreadCount(orgIds));
    }
}
