package com.triobase.service.ops.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.PageResult;
import com.triobase.service.ops.dto.MessageAdminResponse;
import com.triobase.service.ops.dto.MessageCountRow;
import com.triobase.service.ops.dto.MessageInboxResponse;
import com.triobase.service.ops.dto.SendMessageRequest;
import com.triobase.service.ops.entity.OpsMessage;
import com.triobase.service.ops.entity.OpsMessageRecipient;
import com.triobase.service.ops.mapper.MessageMapper;
import com.triobase.service.ops.mapper.MessageRecipientMapper;
import com.triobase.service.ops.notification.service.LegacyNotificationDualWriteService;
import com.triobase.service.ops.notification.service.NotificationCutoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 维护兼容窗口内的旧版单向站内消息与收件人状态。
 *
 * <p>发送事务同步写入新版通知任务和收件投影，任何一侧失败均回滚；用户删除仅作用于
 * 个人可见状态，不得删除其他收件人或共享审计证据。</p>
 */
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final short READ = 1;
    private static final short UNREAD = 0;

    private final MessageMapper messageMapper;
    private final MessageRecipientMapper recipientMapper;
    private final RequestContextService contextService;
    private final LegacyNotificationDualWriteService dualWriteService;
    private final NotificationCutoverService cutoverService;

    @Transactional
    public OpsMessage send(SendMessageRequest request) {
        requireLegacyWrite();
        OpsMessage message = new OpsMessage();
        message.setId(UlidGenerator.nextUlid());
        message.setTenantId(contextService.tenantId());
        message.setTitle(request.getTitle());
        message.setContent(request.getContent());
        message.setMessageType(StringUtils.hasText(request.getMessageType()) ? request.getMessageType() : "SYSTEM");
        message.setSourceType(request.getSourceType());
        message.setSourceId(request.getSourceId());
        message.setSenderId(contextService.userId());
        message.setSenderName(contextService.username());
        messageMapper.insert(message);

        request.getRecipientUserIds().stream()
                .filter(StringUtils::hasText)
                .distinct()
                .forEach(userId -> {
                    OpsMessageRecipient recipient = new OpsMessageRecipient();
                    recipient.setId(UlidGenerator.nextUlid());
                    recipient.setTenantId(contextService.tenantId());
                    recipient.setMessageId(message.getId());
                    recipient.setRecipientUserId(userId);
                    recipient.setReadStatus(UNREAD);
                    recipientMapper.insert(recipient);
                });
        dualWriteService.messageCreated(message.getTenantId(), message.getId());
        return message;
    }

    public PageResult<MessageAdminResponse> adminPage(int page,
                                                      int size,
                                                      String keyword,
                                                      String messageType) {
        LambdaQueryWrapper<OpsMessage> wrapper = new LambdaQueryWrapper<OpsMessage>()
                .eq(OpsMessage::getTenantId, contextService.tenantId())
                .and(StringUtils.hasText(keyword),
                        w -> w.like(OpsMessage::getTitle, keyword)
                                .or()
                                .like(OpsMessage::getContent, keyword))
                .eq(StringUtils.hasText(messageType), OpsMessage::getMessageType, messageType)
                .orderByDesc(OpsMessage::getCreatedAt);
        IPage<OpsMessage> result = messageMapper.selectPage(new Page<>(page, size), wrapper);
        List<String> messageIds = result.getRecords().stream().map(OpsMessage::getId).toList();
        Map<String, MessageCountRow> counts = messageIds.isEmpty() ? Map.of()
                : recipientMapper.countByMessageIds(contextService.tenantId(), messageIds).stream()
                        .collect(Collectors.toMap(MessageCountRow::messageId, Function.identity()));
        return PageResult.of(result.getRecords().stream()
                        .map(message -> toAdminResponse(message, counts.get(message.getId()))).toList(),
                result.getTotal(), page, size);
    }

    public PageResult<MessageInboxResponse> inbox(int page,
                                                  int size,
                                                  Short readStatus) {
        String userId = contextService.userId();
        LambdaQueryWrapper<OpsMessageRecipient> wrapper = new LambdaQueryWrapper<OpsMessageRecipient>()
                .eq(OpsMessageRecipient::getTenantId, contextService.tenantId())
                .eq(OpsMessageRecipient::getRecipientUserId, userId)
                .isNull(OpsMessageRecipient::getDeletedAt)
                .eq(readStatus != null, OpsMessageRecipient::getReadStatus, readStatus)
                .orderByDesc(OpsMessageRecipient::getCreatedAt);
        IPage<OpsMessageRecipient> result = recipientMapper.selectPage(new Page<>(page, size), wrapper);
        List<String> messageIds = result.getRecords().stream()
                .map(OpsMessageRecipient::getMessageId).distinct().toList();
        Map<String, OpsMessage> messages = messageIds.isEmpty() ? Map.of()
                : messageMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OpsMessage>()
                                .eq("tenant_id", contextService.tenantId())
                                .in("id", messageIds)).stream()
                        .collect(Collectors.toMap(OpsMessage::getId, Function.identity()));
        List<MessageInboxResponse> records = result.getRecords().stream()
                .map(recipient -> toInboxResponse(recipient, messages.get(recipient.getMessageId())))
                .filter(Objects::nonNull).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    @Transactional
    public void markRead(String recipientId) {
        requireLegacyWrite();
        OpsMessageRecipient recipient = requireOwnRecipient(recipientId);
        recipient.setReadStatus(READ);
        recipient.setReadAt(LocalDateTime.now());
        recipientMapper.updateById(recipient);
        dualWriteService.recipientChanged(recipient.getTenantId(), recipient.getId());
    }

    @Transactional
    public void deleteInboxMessage(String recipientId) {
        requireLegacyWrite();
        OpsMessageRecipient recipient = requireOwnRecipient(recipientId);
        recipient.setDeletedAt(LocalDateTime.now());
        recipientMapper.updateById(recipient);
        dualWriteService.recipientChanged(recipient.getTenantId(), recipient.getId());
    }

    @Transactional
    public void deleteMessage(String id) {
        requireLegacyWrite();
        // 已投递消息是共享审计证据，管理端删除入口保留兼容响应但永远禁止物理删除。
        throw new BizException(45102, "MESSAGE_PHYSICAL_DELETE_FORBIDDEN");
    }

    public long unreadCount() {
        return recipientMapper.selectCount(new LambdaQueryWrapper<OpsMessageRecipient>()
                .eq(OpsMessageRecipient::getTenantId, contextService.tenantId())
                .eq(OpsMessageRecipient::getRecipientUserId, contextService.userId())
                .eq(OpsMessageRecipient::getReadStatus, UNREAD)
                .isNull(OpsMessageRecipient::getDeletedAt));
    }

    private MessageAdminResponse toAdminResponse(OpsMessage message, MessageCountRow counts) {
        long recipientCount = counts == null ? 0 : counts.recipientCount();
        long readCount = counts == null ? 0 : counts.readCount();
        MessageAdminResponse response = new MessageAdminResponse();
        response.setMessage(message);
        response.setRecipientCount(recipientCount);
        response.setReadCount(readCount);
        response.setUnreadCount(recipientCount - readCount);
        return response;
    }

    private void requireLegacyWrite() {
        cutoverService.requireLegacyWrite(contextService.tenantId());
    }

    private MessageInboxResponse toInboxResponse(OpsMessageRecipient recipient, OpsMessage message) {
        if (message == null) {
            return null;
        }
        MessageInboxResponse response = new MessageInboxResponse();
        response.setMessage(message);
        response.setRecipient(recipient);
        return response;
    }

    private OpsMessageRecipient requireOwnRecipient(String recipientId) {
        OpsMessageRecipient recipient = recipientMapper.selectById(recipientId);
        if (recipient == null || recipient.getDeletedAt() != null) {
            throw new BizException(45102, "MESSAGE_RECIPIENT_NOT_FOUND");
        }
        if (!contextService.userId().equals(recipient.getRecipientUserId())) {
            throw new BizException(45103, "MESSAGE_RECIPIENT_FORBIDDEN");
        }
        return recipient;
    }
}
