import { requestClient } from '#/api/request';

export namespace OpenApiOperationsApi {
  export interface PageResult<T> {
    page: number;
    records: T[];
    size: number;
    total: number;
  }

  export interface Execution {
    applicationClientId?: string;
    callerId?: string;
    completedAt?: string;
    durationMillis?: number;
    environment: string;
    errorCode?: string;
    executionMode: string;
    executionState: string;
    id: string;
    releaseSnapshotId: string;
    retentionUntil: string;
    routeDefinitionId: string;
    sanitizedError?: string;
    startedAt: string;
    tenantId: string;
    traceId?: string;
    workflowId?: string;
  }

  export interface CallbackInbox {
    applicationClientId: string;
    callbackProfileVersionId: string;
    correlationValue: string;
    executionId?: string;
    id: string;
    inboxState: string;
    partnerEventId: string;
    quarantineReason?: string;
    receivedAt: string;
    resolutionNote?: string;
    signalAttempts: number;
    tenantId: string;
  }

  export interface LifecycleAsset {
    assetKey?: string;
    assetType: string;
    createdAt?: string;
    detail: Record<string, any>;
    displayName?: string;
    id: string;
    lifecycleState?: string;
    tenantId?: string;
    updatedAt?: string;
  }

  export interface ReadinessStage {
    key: string;
    ready: boolean;
    route: string;
    title: string;
  }

  export interface LifecycleReadiness {
    assetCounts: Record<string, number>;
    blockers: string[];
    publicRuntimeEnabled: boolean;
    ready: boolean;
    stages: ReadinessStage[];
  }
}

async function getLifecycleAssets(assetType: string, params?: Record<string, any>) {
  return requestClient.get<OpenApiOperationsApi.PageResult<OpenApiOperationsApi.LifecycleAsset>>(
    `/openapi/management/operations/assets/${assetType}`,
    { params },
  );
}

async function getLifecycleReadiness() {
  return requestClient.get<OpenApiOperationsApi.LifecycleReadiness>(
    '/openapi/management/operations/readiness',
  );
}

async function getOpenApiLifecycleData<T = Record<string, any>>(url: string) {
  return requestClient.get<T>(url);
}

async function invokeOpenApiLifecycleAction(
  method: 'POST' | 'PUT',
  url: string,
  data?: Record<string, any>,
) {
  return method === 'PUT' ? requestClient.put(url, data) : requestClient.post(url, data);
}

async function getOpenApiExecutions(params?: Record<string, any>) {
  return requestClient.get<OpenApiOperationsApi.PageResult<OpenApiOperationsApi.Execution>>(
    '/openapi/management/executions',
    { params },
  );
}

async function getCallbackQuarantine(params?: Record<string, any>) {
  return requestClient.get<OpenApiOperationsApi.CallbackInbox[]>(
    '/openapi/management/callback-quarantine',
    { params },
  );
}

async function resolveCallbackQuarantine(
  inboxId: string,
  data: { action: 'DISCARD' | 'LINK' | 'RETRY'; executionId?: string; note: string },
) {
  return requestClient.post<OpenApiOperationsApi.CallbackInbox>(
    `/openapi/management/callback-quarantine/${inboxId}/resolve`,
    data,
  );
}

export {
  getCallbackQuarantine,
  getLifecycleAssets,
  getLifecycleReadiness,
  getOpenApiLifecycleData,
  getOpenApiExecutions,
  invokeOpenApiLifecycleAction,
  resolveCallbackQuarantine,
};
