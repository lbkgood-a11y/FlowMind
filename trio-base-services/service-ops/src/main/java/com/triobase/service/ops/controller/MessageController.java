package com.triobase.service.ops.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.ops.dto.MessageAdminResponse;
import com.triobase.service.ops.dto.MessageInboxResponse;
import com.triobase.service.ops.dto.SendMessageRequest;
import com.triobase.service.ops.entity.OpsMessage;
import com.triobase.service.ops.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    @RequirePermission("/api/v1/messages:GET")
    public R<PageResult<MessageAdminResponse>> adminPage(@RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int size,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String messageType) {
        return R.ok(messageService.adminPage(page, size, keyword, messageType));
    }

    @PostMapping
    @RequirePermission("/api/v1/messages:POST")
    public R<OpsMessage> send(@Valid @RequestBody SendMessageRequest request) {
        return R.ok(messageService.send(request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("/api/v1/messages/*:DELETE")
    public R<Void> delete(@PathVariable String id) {
        messageService.deleteMessage(id);
        return R.ok();
    }

    @GetMapping("/inbox")
    public R<PageResult<MessageInboxResponse>> inbox(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @RequestParam(required = false) Short readStatus) {
        return R.ok(messageService.inbox(page, size, readStatus));
    }

    @PostMapping("/inbox/{recipientId}/read")
    public R<Void> markRead(@PathVariable String recipientId) {
        messageService.markRead(recipientId);
        return R.ok();
    }

    @DeleteMapping("/inbox/{recipientId}")
    public R<Void> deleteInboxMessage(@PathVariable String recipientId) {
        messageService.deleteInboxMessage(recipientId);
        return R.ok();
    }

    @GetMapping("/unread-count")
    public R<Long> unreadCount() {
        return R.ok(messageService.unreadCount());
    }
}
