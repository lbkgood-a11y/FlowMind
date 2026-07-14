package com.triobase.service.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.dto.BusinessObjectCatalogResponse;
import com.triobase.service.workflow.dto.BusinessObjectSummaryResponse;
import com.triobase.service.workflow.entity.BusinessObject;
import com.triobase.service.workflow.entity.BusinessObjectAction;
import com.triobase.service.workflow.entity.BusinessObjectAgentAction;
import com.triobase.service.workflow.entity.BusinessObjectEvent;
import com.triobase.service.workflow.entity.BusinessObjectForm;
import com.triobase.service.workflow.entity.BusinessObjectPermission;
import com.triobase.service.workflow.entity.BusinessObjectStatus;
import com.triobase.service.workflow.entity.BusinessObjectTemplate;
import com.triobase.service.workflow.mapper.BusinessObjectActionMapper;
import com.triobase.service.workflow.mapper.BusinessObjectAgentActionMapper;
import com.triobase.service.workflow.mapper.BusinessObjectEventMapper;
import com.triobase.service.workflow.mapper.BusinessObjectFormMapper;
import com.triobase.service.workflow.mapper.BusinessObjectMapper;
import com.triobase.service.workflow.mapper.BusinessObjectPermissionMapper;
import com.triobase.service.workflow.mapper.BusinessObjectStatusMapper;
import com.triobase.service.workflow.mapper.BusinessObjectTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class BusinessObjectCatalogService {

    private static final String GLOBAL_TENANT = "GLOBAL";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_OFFLINE = "OFFLINE";

    private final BusinessObjectMapper businessObjectMapper;
    private final BusinessObjectStatusMapper statusMapper;
    private final BusinessObjectFormMapper formMapper;
    private final BusinessObjectPermissionMapper permissionMapper;
    private final BusinessObjectActionMapper actionMapper;
    private final BusinessObjectEventMapper eventMapper;
    private final BusinessObjectAgentActionMapper agentActionMapper;
    private final BusinessObjectTemplateMapper templateMapper;

    public List<BusinessObjectSummaryResponse> listPublishedForCurrentTenant() {
        String tenantId = currentTenantId();
        Map<String, BusinessObject> effective = new LinkedHashMap<>();

        latestByType(GLOBAL_TENANT).values().stream()
                .filter(object -> STATUS_PUBLISHED.equals(object.getStatus()))
                .sorted(Comparator.comparing(BusinessObject::getDisplayName))
                .forEach(object -> effective.put(object.getTypeCode(), object));

        if (!GLOBAL_TENANT.equals(tenantId)) {
            for (BusinessObject tenantObject : latestByType(tenantId).values()) {
                if (STATUS_PUBLISHED.equals(tenantObject.getStatus())) {
                    effective.put(tenantObject.getTypeCode(), tenantObject);
                } else if (STATUS_OFFLINE.equals(tenantObject.getStatus())) {
                    effective.remove(tenantObject.getTypeCode());
                }
            }
        }

        return effective.values().stream().map(this::toSummary).toList();
    }

    public BusinessObjectCatalogResponse getPublishedDetail(String typeCode) {
        return getPublishedDetail(typeCode, currentTenantId());
    }

    public BusinessObjectCatalogResponse getPublishedDetail(String typeCode, String tenantId) {
        if (!StringUtils.hasText(typeCode)) {
            throw new BizException(40000, "BUSINESS_OBJECT_TYPE_REQUIRED");
        }
        String effectiveTenant = normalizeTenant(tenantId);
        BusinessObject global = latestByType(GLOBAL_TENANT).get(typeCode);
        BusinessObject tenant = GLOBAL_TENANT.equals(effectiveTenant)
                ? null
                : latestByType(effectiveTenant).get(typeCode);

        if (tenant != null && STATUS_OFFLINE.equals(tenant.getStatus())) {
            throw new BizException(40400, "BUSINESS_OBJECT_OFFLINE");
        }
        if (tenant != null && STATUS_PUBLISHED.equals(tenant.getStatus())) {
            return toCatalog(tenant, global);
        }
        if (global != null && STATUS_PUBLISHED.equals(global.getStatus())) {
            return toCatalog(global, null);
        }
        throw new BizException(40400, "BUSINESS_OBJECT_NOT_FOUND");
    }

    private BusinessObjectCatalogResponse toCatalog(BusinessObject effective, BusinessObject fallback) {
        BusinessObjectCatalogResponse response = new BusinessObjectCatalogResponse();
        response.setObject(toSummary(effective));

        List<String> objectIds = new ArrayList<>();
        if (fallback != null) {
            objectIds.add(fallback.getId());
        }
        objectIds.add(effective.getId());

        response.setStatuses(mergeChildren(objectIds,
                id -> statusMapper.selectList(new LambdaQueryWrapper<BusinessObjectStatus>()
                        .eq(BusinessObjectStatus::getObjectId, id)
                        .orderByAsc(BusinessObjectStatus::getSortOrder)),
                BusinessObjectStatus::getStatusCode,
                this::toStatus));
        response.setForms(mergeChildren(objectIds,
                id -> formMapper.selectList(new LambdaQueryWrapper<BusinessObjectForm>()
                        .eq(BusinessObjectForm::getObjectId, id)
                        .orderByAsc(BusinessObjectForm::getSortOrder)),
                item -> item.getFormRole() + ":" + item.getFormKey(),
                this::toForm));
        response.setPermissions(mergeChildren(objectIds,
                id -> permissionMapper.selectList(new LambdaQueryWrapper<BusinessObjectPermission>()
                        .eq(BusinessObjectPermission::getObjectId, id)
                        .orderByAsc(BusinessObjectPermission::getSortOrder)),
                BusinessObjectPermission::getActionCode,
                this::toPermission));
        response.setActions(mergeChildren(objectIds,
                id -> actionMapper.selectList(new LambdaQueryWrapper<BusinessObjectAction>()
                        .eq(BusinessObjectAction::getObjectId, id)
                        .orderByAsc(BusinessObjectAction::getSortOrder)),
                BusinessObjectAction::getActionCode,
                this::toAction));
        response.setEvents(mergeChildren(objectIds,
                id -> eventMapper.selectList(new LambdaQueryWrapper<BusinessObjectEvent>()
                        .eq(BusinessObjectEvent::getObjectId, id)
                        .orderByAsc(BusinessObjectEvent::getSortOrder)),
                BusinessObjectEvent::getEventCode,
                this::toEvent));
        response.setAgentActions(mergeChildren(objectIds,
                id -> agentActionMapper.selectList(new LambdaQueryWrapper<BusinessObjectAgentAction>()
                        .eq(BusinessObjectAgentAction::getObjectId, id)
                        .orderByAsc(BusinessObjectAgentAction::getSortOrder)),
                BusinessObjectAgentAction::getAgentActionCode,
                this::toAgentAction));
        response.setTemplates(mergeChildren(objectIds,
                id -> templateMapper.selectList(new LambdaQueryWrapper<BusinessObjectTemplate>()
                        .eq(BusinessObjectTemplate::getObjectId, id)
                        .orderByAsc(BusinessObjectTemplate::getSortOrder)),
                BusinessObjectTemplate::getTemplateCode,
                this::toTemplate));

        return response;
    }

    private Map<String, BusinessObject> latestByType(String tenantId) {
        List<BusinessObject> objects = businessObjectMapper.selectList(
                new LambdaQueryWrapper<BusinessObject>()
                        .eq(BusinessObject::getTenantId, tenantId)
                        .orderByAsc(BusinessObject::getVersion));
        Map<String, BusinessObject> latest = new LinkedHashMap<>();
        for (BusinessObject object : objects) {
            latest.put(object.getTypeCode(), object);
        }
        return latest;
    }

    private <E, R> List<R> mergeChildren(List<String> objectIds,
                                         Function<String, List<E>> loader,
                                         Function<E, String> keyFn,
                                         Function<E, R> mapper) {
        Map<String, E> merged = new LinkedHashMap<>();
        for (String objectId : objectIds) {
            for (E item : loader.apply(objectId)) {
                merged.put(keyFn.apply(item), item);
            }
        }
        return merged.values().stream().map(mapper).toList();
    }

    private BusinessObjectSummaryResponse toSummary(BusinessObject object) {
        return new BusinessObjectSummaryResponse(
                object.getId(),
                object.getTenantId(),
                object.getTypeCode(),
                object.getDisplayName(),
                object.getServiceCode(),
                object.getVersion(),
                object.getStatus(),
                object.getDescription());
    }

    private BusinessObjectCatalogResponse.StatusItem toStatus(BusinessObjectStatus status) {
        BusinessObjectCatalogResponse.StatusItem item = new BusinessObjectCatalogResponse.StatusItem();
        item.setStatusCode(status.getStatusCode());
        item.setDisplayName(status.getDisplayName());
        item.setStatusGroup(status.getStatusGroup());
        item.setInitial(status.getInitial());
        item.setTerminal(status.getTerminal());
        item.setSortOrder(status.getSortOrder());
        return item;
    }

    private BusinessObjectCatalogResponse.FormItem toForm(BusinessObjectForm form) {
        BusinessObjectCatalogResponse.FormItem item = new BusinessObjectCatalogResponse.FormItem();
        item.setFormRole(form.getFormRole());
        item.setDisplayName(form.getDisplayName());
        item.setFormDefinitionId(form.getFormDefinitionId());
        item.setFormKey(form.getFormKey());
        item.setFormVersion(form.getFormVersion());
        item.setRequired(form.getRequired());
        item.setSortOrder(form.getSortOrder());
        return item;
    }

    private BusinessObjectCatalogResponse.PermissionItem toPermission(BusinessObjectPermission permission) {
        BusinessObjectCatalogResponse.PermissionItem item = new BusinessObjectCatalogResponse.PermissionItem();
        item.setActionCode(permission.getActionCode());
        item.setDisplayName(permission.getDisplayName());
        item.setPermissionCode(permission.getPermissionCode());
        item.setActionGroup(permission.getActionGroup());
        item.setSortOrder(permission.getSortOrder());
        return item;
    }

    private BusinessObjectCatalogResponse.ActionItem toAction(BusinessObjectAction action) {
        BusinessObjectCatalogResponse.ActionItem item = new BusinessObjectCatalogResponse.ActionItem();
        item.setActionCode(action.getActionCode());
        item.setDisplayName(action.getDisplayName());
        item.setActionType(action.getActionType());
        item.setExecutorKey(action.getExecutorKey());
        item.setModeDefault(action.getModeDefault());
        item.setPermissionAction(action.getPermissionAction());
        item.setParamSchemaJson(action.getParamSchemaJson());
        item.setSortOrder(action.getSortOrder());
        return item;
    }

    private BusinessObjectCatalogResponse.EventItem toEvent(BusinessObjectEvent event) {
        BusinessObjectCatalogResponse.EventItem item = new BusinessObjectCatalogResponse.EventItem();
        item.setEventCode(event.getEventCode());
        item.setDisplayName(event.getDisplayName());
        item.setEventType(event.getEventType());
        item.setPayloadSchemaJson(event.getPayloadSchemaJson());
        item.setSortOrder(event.getSortOrder());
        return item;
    }

    private BusinessObjectCatalogResponse.AgentActionItem toAgentAction(BusinessObjectAgentAction action) {
        BusinessObjectCatalogResponse.AgentActionItem item = new BusinessObjectCatalogResponse.AgentActionItem();
        item.setAgentActionCode(action.getAgentActionCode());
        item.setDisplayName(action.getDisplayName());
        item.setExecutorKey(action.getExecutorKey());
        item.setPermissionAction(action.getPermissionAction());
        item.setParamSchemaJson(action.getParamSchemaJson());
        item.setResultSchemaJson(action.getResultSchemaJson());
        item.setModeDefault(action.getModeDefault());
        item.setSortOrder(action.getSortOrder());
        return item;
    }

    private BusinessObjectCatalogResponse.TemplateItem toTemplate(BusinessObjectTemplate template) {
        BusinessObjectCatalogResponse.TemplateItem item = new BusinessObjectCatalogResponse.TemplateItem();
        item.setTemplateCode(template.getTemplateCode());
        item.setDisplayName(template.getDisplayName());
        item.setTemplateType(template.getTemplateType());
        item.setConfigJson(template.getConfigJson());
        item.setSortOrder(template.getSortOrder());
        return item;
    }

    private String currentTenantId() {
        return normalizeTenant(SecurityContextHolder.getTenantId());
    }

    private String normalizeTenant(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId : GLOBAL_TENANT;
    }
}
