package com.triobase.service.ops.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.dto.notification.NotificationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 从受 ACL 保护的请求主题接收 Owner 通知请求，并复用统一准入与幂等路径。
 *
 * <p>主题 ACL 必须限制生产者只能写入自己的服务身份。载荷无法解析时抛出异常交由 Kafka
 * 错误处理器重试/隔离，禁止吞掉后提交 offset。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "triobase.notification.kafka", name = "enabled", havingValue = "true")
public class NotificationKafkaRequestConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationAdmissionService admissionService;

    @KafkaListener(
            topics = "${triobase.notification.kafka.request-topic:triobase.notification.requests.v1}",
            groupId = "${spring.application.name:service-ops}-notification-admission")
    public void consume(String payload) throws JsonProcessingException {
        NotificationRequest request = objectMapper.readValue(payload, NotificationRequest.class);
        // producer 仅在受 ACL 保护的主题中作为调用身份；HTTP 入口必须使用认证过滤器提供的 caller。
        admissionService.admit(request.getProducer(), request);
    }
}
