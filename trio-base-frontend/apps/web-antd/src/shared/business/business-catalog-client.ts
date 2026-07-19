import { requestClient } from '#/api/request';

export namespace BusinessCatalogApi {
  export interface BusinessActionMetadata {
    actionType: string;
    danger?: boolean;
    displayName?: string;
    executionMode?: string;
    primary?: boolean;
    refreshScopes?: string[];
    requiredPermission?: string;
    targetStatus?: string;
    targetStatusGroup?: string;
  }

  export interface BusinessObjectMetadata {
    actions?: BusinessActionMetadata[];
    displayName?: string;
    objectType: string;
    ownerService?: string;
    pageMetadata?: Record<string, unknown>;
    statusMetadata?: Record<string, unknown>[];
    tenantId?: string;
    version?: number;
  }

  export interface BusinessTimelineEntry {
    actionId?: string;
    actionStatus?: string;
    actionType?: string;
    actorId?: string;
    actorName?: string;
    correlationId?: string;
    displayName?: string;
    eventId: string;
    eventSource?: string;
    eventType?: string;
    occurredAt?: string;
    ownerExecutionRef?: string;
    ownerService?: string;
    redacted?: boolean;
    sequenceNo?: number;
    summary?: Record<string, unknown>;
    targetId?: string;
    targetType?: string;
    tenantId?: string;
    traceId?: string;
  }

  export interface BusinessTimelineQuery {
    actionId?: string;
    actionStatus?: string;
    actionType?: string;
    actorId?: string;
    correlationId?: string;
    eventSource?: string;
    eventType?: string;
    ownerExecutionRef?: string;
    page?: number;
    size?: number;
    targetId?: string;
    targetType?: string;
    tenantId: string;
    traceId?: string;
  }

  export interface PageResult<T> {
    records?: T[];
    total: number;
  }
}

async function getBusinessObjects(params?: { tenantId?: string }) {
  return requestClient.get<BusinessCatalogApi.BusinessObjectMetadata[]>(
    '/business-catalog/objects',
    { params },
  );
}

async function getBusinessObject(objectType: string, params?: { tenantId?: string }) {
  return requestClient.get<BusinessCatalogApi.BusinessObjectMetadata>(
    `/business-catalog/objects/${objectType}`,
    { params },
  );
}

async function queryBusinessTimeline(params: BusinessCatalogApi.BusinessTimelineQuery) {
  return requestClient.get<
    BusinessCatalogApi.PageResult<BusinessCatalogApi.BusinessTimelineEntry>
  >('/business-timeline', { params });
}

export { getBusinessObject, getBusinessObjects, queryBusinessTimeline };
