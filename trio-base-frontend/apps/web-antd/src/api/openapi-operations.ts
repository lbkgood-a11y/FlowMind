import { requestClient } from '#/api/request';

import {
  ACTION_TARGET_TYPES,
  ACTION_TYPES,
  type ActionApi,
  submitAction,
} from './action-client';
import { requireActionData } from './action-status';

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

  export interface RuntimeAdmission {
    applicationClientId?: string;
    environment: 'DEV' | 'PROD' | 'TEST' | string;
    maxActiveWorkflows?: number;
    maxConcurrency?: number;
    policyVersion?: number;
    subscriptionId?: string;
    tenantId?: string;
  }

  export interface OrchestrationExecution {
    completedAt?: string;
    executionId: string;
    routeKey: string;
    startedAt?: string;
    state: string;
    workflowId?: string;
  }

  export interface StartOrchestrationActionRequest {
    admission: RuntimeAdmission;
    environment: 'DEV' | 'PROD' | 'TEST' | string;
    idempotencyKey: string;
    operation?: string;
    payload?: Record<string, unknown>;
    routeKey: string;
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

async function startOpenApiOrchestrationAction(
  data: OpenApiOperationsApi.StartOrchestrationActionRequest,
) {
  const result = await submitAction<{
    orchestration: OpenApiOperationsApi.OrchestrationExecution;
  }>({
    actionType: ACTION_TYPES.integrationOrchestrationStart,
    executionMode: 'WORKFLOW',
    idempotencyKey: data.idempotencyKey,
    payload: {
      admission: data.admission,
      environment: data.environment,
      idempotencyKey: data.idempotencyKey,
      operation: data.operation ?? 'POST',
      payload: data.payload ?? {},
      routeKey: data.routeKey,
    },
    source: 'GUI',
    target: {
      id: data.routeKey,
      ownerService: 'service-openapi',
      tenantId: data.admission.tenantId,
      type: ACTION_TARGET_TYPES.integrationRoute,
    },
  } satisfies ActionApi.GlobalActionRequest);
  return requireActionData<OpenApiOperationsApi.OrchestrationExecution>(
    result,
    'orchestration',
  );
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
  startOpenApiOrchestrationAction,
};
