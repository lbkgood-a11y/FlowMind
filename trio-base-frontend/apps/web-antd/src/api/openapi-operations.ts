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

  export interface ExecutionStepAttempt {
    actionActorId?: string;
    actionActorName?: string;
    actionActorType?: string;
    actionCorrelationId?: string;
    actionId?: string;
    actionSource?: string;
    actionTraceId?: string;
    actionType?: string;
    attemptNumber?: number;
    attemptState?: string;
    completedAt?: string;
    createdAt?: string;
    durationMillis?: number;
    errorCode?: string;
    evidence?: Record<string, unknown>;
    executionId: string;
    externalStatus?: number;
    id: string;
    sanitizedError?: string;
    startedAt?: string;
    stepKey?: string;
    stepType?: string;
  }

  export interface ExecutionDetail {
    attempts: ExecutionStepAttempt[];
    execution: Execution;
  }

  export interface DiagnosticCaptureRequest {
    redactionPolicy?: Record<string, unknown>;
    requestPayload?: Record<string, unknown>;
    responsePayload?: Record<string, unknown>;
  }

  export interface DiagnosticCaptureResponse {
    diagnosticId: string;
    executionId: string;
    expiresAt: string;
  }

  export interface CallbackInbox {
    actionActorId?: string;
    actionActorName?: string;
    actionActorType?: string;
    actionCorrelationId?: string;
    actionId?: string;
    actionSource?: string;
    actionTraceId?: string;
    actionType?: string;
    applicationClientId: string;
    bodyHash?: string;
    callbackProfileVersionId: string;
    correlationValue: string;
    executionId?: string;
    id: string;
    inboxState: string;
    lastSignalError?: string;
    mappedPayload?: Record<string, unknown>;
    nextSignalAt?: string;
    partnerEventId: string;
    quarantineReason?: string;
    receivedAt: string;
    resolvedAt?: string;
    resolvedBy?: string;
    resolutionState?: string;
    resolutionNote?: string;
    retentionUntil?: string;
    signalName?: string;
    signalAttempts: number;
    tenantId: string;
    updatedAt?: string;
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

async function getOpenApiExecutionDetail(executionId: string) {
  return requestClient.get<OpenApiOperationsApi.ExecutionDetail>(
    `/openapi/management/executions/${executionId}`,
  );
}

async function captureOpenApiExecutionDiagnostic(
  executionId: string,
  data: OpenApiOperationsApi.DiagnosticCaptureRequest,
) {
  return requestClient.post<OpenApiOperationsApi.DiagnosticCaptureResponse>(
    `/openapi/management/executions/${executionId}/diagnostics`,
    data,
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
  captureOpenApiExecutionDiagnostic,
  getCallbackQuarantine,
  getLifecycleAssets,
  getLifecycleReadiness,
  getOpenApiExecutionDetail,
  getOpenApiLifecycleData,
  getOpenApiExecutions,
  invokeOpenApiLifecycleAction,
  resolveCallbackQuarantine,
  startOpenApiOrchestrationAction,
};
