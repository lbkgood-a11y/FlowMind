package com.triobase.service.workflow.service;

import com.triobase.common.dto.catalog.BusinessActionMetadata;
import com.triobase.common.dto.catalog.BusinessObjectMetadata;
import com.triobase.common.dto.catalog.BusinessStatusMetadata;
import com.triobase.service.workflow.dto.BusinessObjectCatalogResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowBusinessCatalogBridgeService {

    private final BusinessObjectCatalogService catalogService;

    public BusinessObjectMetadata getSharedMetadata(String typeCode) {
        return toSharedMetadata(catalogService.getPublishedDetail(typeCode));
    }

    public BusinessObjectMetadata toSharedMetadata(BusinessObjectCatalogResponse response) {
        BusinessObjectMetadata metadata = new BusinessObjectMetadata();
        if (response == null || response.getObject() == null) {
            return metadata;
        }
        metadata.setTenantId(response.getObject().getTenantId());
        metadata.setObjectType(response.getObject().getTypeCode());
        metadata.setDisplayName(response.getObject().getDisplayName());
        metadata.setOwnerService(response.getObject().getServiceCode());
        metadata.setDescription(response.getObject().getDescription());
        metadata.setVersion(response.getObject().getVersion());
        metadata.setLifecycleStatus(response.getObject().getStatus());
        metadata.setStatuses(response.getStatuses().stream().map(this::toStatus).toList());
        metadata.setActions(response.getActions().stream().map(this::toAction).toList());
        return metadata;
    }

    private BusinessStatusMetadata toStatus(BusinessObjectCatalogResponse.StatusItem item) {
        BusinessStatusMetadata status = new BusinessStatusMetadata();
        status.setStatusCode(item.getStatusCode());
        status.setDisplayName(item.getDisplayName());
        status.setStatusGroup(item.getStatusGroup());
        status.setInitial(Boolean.TRUE.equals(item.getInitial()));
        status.setTerminal(Boolean.TRUE.equals(item.getTerminal()));
        status.setSortOrder(item.getSortOrder());
        return status;
    }

    private BusinessActionMetadata toAction(BusinessObjectCatalogResponse.ActionItem item) {
        BusinessActionMetadata action = new BusinessActionMetadata();
        action.setActionCode(item.getActionCode());
        action.setDisplayName(item.getDisplayName());
        action.setActionType(item.getActionType());
        action.setExecutionMode(item.getModeDefault());
        action.setPermissionCode(item.getPermissionAction());
        action.setPayloadSchemaJson(item.getParamSchemaJson());
        action.setSortOrder(item.getSortOrder());
        return action;
    }
}
