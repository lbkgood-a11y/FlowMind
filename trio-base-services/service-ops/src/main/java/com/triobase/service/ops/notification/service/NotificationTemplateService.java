package com.triobase.service.ops.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.ops.notification.dto.NotificationTemplateDraftRequest;
import com.triobase.service.ops.notification.dto.NotificationTemplatePreview;
import com.triobase.service.ops.notification.dto.NotificationTemplateView;
import com.triobase.service.ops.notification.entity.NotificationTemplateEntity;
import com.triobase.service.ops.notification.entity.NotificationTemplateVersionEntity;
import com.triobase.service.ops.notification.mapper.NotificationTemplateMapper;
import com.triobase.service.ops.notification.mapper.NotificationTemplateVersionMapper;
import com.triobase.service.ops.service.RequestContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/** 管理租户、渠道和 locale 维度的不可变模板版本及审核发布状态。 */
@Service
@RequiredArgsConstructor
public class NotificationTemplateService {
    private final NotificationTemplateMapper templateMapper;
    private final NotificationTemplateVersionMapper versionMapper;
    private final SafeNotificationTemplateRenderer renderer;
    private final RequestContextService contextService;
    private final ObjectMapper objectMapper;
    private final NotificationConfigurationAuditService auditService;

    public List<NotificationTemplateView> listCurrentTenant() {
        String tenantId = contextService.tenantId();
        return templateMapper.selectList(new LambdaQueryWrapper<NotificationTemplateEntity>()
                        .eq(NotificationTemplateEntity::getTenantId, tenantId)
                        .orderByAsc(NotificationTemplateEntity::getTemplateKey))
                .stream().flatMap(template -> versionMapper.findVersions(tenantId, template.getId())
                        .stream().map(version -> view(template, version)))
                .toList();
    }

    @Transactional
    public NotificationTemplateView createDraft(NotificationTemplateDraftRequest request) {
        validatePeriod(request.effectiveFrom(), request.effectiveUntil());
        Map<String, String> schema = request.variableSchema() == null
                ? Map.of() : new LinkedHashMap<>(request.variableSchema());
        renderer.validate(request.subjectTemplate(), request.bodyTemplate(), schema);
        String tenantId = contextService.tenantId();
        NotificationTemplateEntity template = templateMapper.findOwned(tenantId, request.templateKey(),
                request.channelCode(), request.localeCode());
        if (template == null) {
            template = new NotificationTemplateEntity();
            template.setId(id());
            template.setTenantId(tenantId);
            template.setTemplateKey(request.templateKey());
            template.setChannelCode(request.channelCode());
            template.setLocaleCode(request.localeCode());
            templateMapper.insert(template);
        }
        NotificationTemplateVersionEntity version = new NotificationTemplateVersionEntity();
        version.setId(id());
        version.setTenantId(tenantId);
        version.setTemplateId(template.getId());
        version.setVersionNo(versionMapper.maxVersion(tenantId, template.getId()) + 1);
        version.setTemplateState("DRAFT");
        version.setSubjectTemplate(request.subjectTemplate());
        version.setBodyTemplate(request.bodyTemplate());
        version.setVariableSchemaJson(write(schema));
        version.setEffectiveFrom(request.effectiveFrom());
        version.setEffectiveUntil(request.effectiveUntil());
        version.setCreatedBy(contextService.userId());
        versionMapper.insert(version);
        auditService.record("TEMPLATE_VERSION", template.getTemplateKey() + ":" + version.getVersionNo(),
                "DRAFT_CREATED", template.getChannelCode() + ":" + template.getLocaleCode());
        return view(template, version);
    }

    public NotificationTemplatePreview preview(String versionId, Map<String, Object> variables) {
        String tenantId = contextService.tenantId();
        NotificationTemplateVersionEntity version = requireVersion(tenantId, versionId);
        Map<String, String> schema = read(version.getVariableSchemaJson());
        return new NotificationTemplatePreview(
                version.getSubjectTemplate() == null ? null
                        : renderer.render(version.getSubjectTemplate(), schema, variables),
                renderer.render(version.getBodyTemplate(), schema, variables));
    }

    @Transactional
    public void submitReview(String versionId) {
        transition(versionId, "DRAFT", "PENDING_REVIEW");
    }

    @Transactional
    public void reject(String versionId) {
        transition(versionId, "PENDING_REVIEW", "REJECTED");
    }

    @Transactional
    public void publish(String versionId) {
        String tenantId = contextService.tenantId();
        NotificationTemplateVersionEntity version = requireVersion(tenantId, versionId);
        validatePeriod(version.getEffectiveFrom(), version.getEffectiveUntil());
        if (versionMapper.transition(tenantId, versionId, "PENDING_REVIEW", "PUBLISHED") != 1) {
            throw new BizException(45515, "TEMPLATE_TRANSITION_INVALID");
        }
        templateMapper.setCurrentVersion(tenantId, version.getTemplateId(), versionId);
        auditService.record("TEMPLATE_VERSION", versionId, "PUBLISHED", "IMMUTABLE_VERSION");
    }

    private void transition(String versionId, String from, String to) {
        String tenantId = contextService.tenantId();
        requireVersion(tenantId, versionId);
        if (versionMapper.transition(tenantId, versionId, from, to) != 1) {
            throw new BizException(45515, "TEMPLATE_TRANSITION_INVALID");
        }
        auditService.record("TEMPLATE_VERSION", versionId, to, "STATE_CHANGED");
    }

    private NotificationTemplateVersionEntity requireVersion(String tenantId, String id) {
        NotificationTemplateVersionEntity version = versionMapper.findOwned(tenantId, id);
        if (version == null) throw new BizException(45514, "TEMPLATE_VERSION_NOT_FOUND");
        return version;
    }

    private void validatePeriod(LocalDateTime from, LocalDateTime until) {
        if (from != null && until != null && !until.isAfter(from)) {
            throw new BizException(45516, "TEMPLATE_EFFECTIVE_PERIOD_INVALID");
        }
    }

    private NotificationTemplateView view(NotificationTemplateEntity template,
                                            NotificationTemplateVersionEntity version) {
        return new NotificationTemplateView(template.getId(), version.getId(), version.getVersionNo(),
                template.getTemplateKey(), template.getChannelCode(), template.getLocaleCode(),
                version.getTemplateState(), version.getSubjectTemplate(), version.getBodyTemplate(),
                read(version.getVariableSchemaJson()), version.getEffectiveFrom(), version.getEffectiveUntil());
    }

    private String write(Map<String, String> schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException error) {
            throw new BizException(45512, "TEMPLATE_VARIABLE_SCHEMA_INVALID");
        }
    }

    private Map<String, String> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException error) {
            throw new BizException(45512, "TEMPLATE_VARIABLE_SCHEMA_INVALID");
        }
    }

    private String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
