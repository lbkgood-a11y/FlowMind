import { requestClient } from '#/api/request';

export namespace ActionApi {
  export type ActionActorType =
    | 'AGENT'
    | 'SCHEDULER'
    | 'SERVICE'
    | 'SYSTEM'
    | 'USER'
    | 'WORKFLOW';

  export type ActionAuditLevel = 'CRITICAL' | 'NORMAL' | 'SENSITIVE';

  export type ActionErrorCategory =
    | 'AUTHORIZATION'
    | 'CONFLICT'
    | 'DISPATCH'
    | 'EXECUTION'
    | 'GUARD'
    | 'IDEMPOTENCY'
    | 'SECURITY'
    | 'SYSTEM'
    | 'TIMEOUT'
    | 'VALIDATION';

  export type ActionExecutionMode = 'ASYNC' | 'SIGNAL' | 'SYNC' | 'WORKFLOW';

  export type ActionSource =
    | 'AGENT'
    | 'API'
    | 'EVENT'
    | 'GUI'
    | 'LUI'
    | 'SCHEDULER'
    | 'SYSTEM'
    | 'WORKFLOW';

  export type ActionStatus =
    | 'ACCEPTED'
    | 'AUTHORIZED'
    | 'CANCELLED'
    | 'COMPENSATED'
    | 'COMPENSATING'
    | 'CREATED'
    | 'FAILED'
    | 'REJECTED'
    | 'RUNNING'
    | 'SUCCEEDED'
    | 'VALIDATING';

  export interface ActionActor {
    attributes?: Record<string, unknown>;
    delegatedBy?: string;
    displayName?: string;
    id?: string;
    reason?: string;
    tenantId?: string;
    type?: ActionActorType;
  }

  export interface ActionContext {
    attributes?: Record<string, unknown>;
    authVersion?: number;
    authorizationVersion?: number;
    clientIp?: string;
    confirmationId?: string;
    confirmedAt?: string;
    confirmedBy?: string;
    correlationId?: string;
    dataPolicyVersion?: number;
    fieldPolicyVersion?: number;
    guardTemplateVersion?: number;
    locale?: string;
    requestId?: string;
    roleVersion?: number;
    tenantId?: string;
    traceId?: string;
    userAgent?: string;
  }

  export interface ActionTarget {
    attributes?: Record<string, unknown>;
    id?: string;
    ownerService?: string;
    tenantId?: string;
    type?: string;
    version?: string;
  }

  export interface GlobalActionRequest {
    actionId?: string;
    actionType: string;
    actor?: ActionActor;
    context?: ActionContext;
    executionMode?: ActionExecutionMode;
    idempotencyKey?: string;
    payload?: Record<string, unknown>;
    source?: ActionSource;
    target?: ActionTarget;
  }

  export interface ActionError {
    category?: ActionErrorCategory;
    code?: string;
    details?: Record<string, unknown>;
    field?: string;
    message?: string;
  }

  export interface ActionConfirmation {
    confirmLabel?: string;
    message?: string;
    required?: boolean;
    riskLevel?: ActionAuditLevel;
    title?: string;
  }

  export interface ActionCandidate {
    actionType: string;
    actor?: ActionActor;
    candidateId?: string;
    confirmation?: ActionConfirmation;
    context?: ActionContext;
    createdAt?: string;
    executionMode?: ActionExecutionMode;
    idempotencyKey?: string;
    payload?: Record<string, unknown>;
    proposedBy?: string;
    reason?: string;
    requiresConfirmation?: boolean;
    source?: ActionSource;
    target?: ActionTarget;
  }

  export interface ActionCandidateValidationResult {
    actionRequest?: GlobalActionRequest;
    actionType?: string;
    candidateId?: string;
    confirmation?: ActionConfirmation;
    confirmationSatisfied?: boolean;
    danger?: boolean;
    disabledReason?: string;
    definitionExists?: boolean;
    dispatchable?: boolean;
    enabled?: boolean;
    errors?: ActionError[];
    executionMode?: ActionExecutionMode;
    refreshScopes?: string[];
    requiresConfirmation?: boolean;
    schemaValid?: boolean;
    targetStatus?: string;
    targetStatusGroup?: string;
    valid?: boolean;
    visible?: boolean;
  }

  export interface GlobalActionResult<TData = Record<string, unknown>> {
    actionId?: string;
    actionType?: string;
    createdAt?: string;
    data?: TData;
    errors?: ActionError[];
    message?: string;
    ownerExecutionMetadata?: Record<string, unknown>;
    ownerExecutionRef?: string;
    ownerService?: string;
    refreshScopes?: string[];
    retryable?: boolean;
    status?: ActionStatus;
    target?: ActionTarget;
    targetStatus?: string;
    targetStatusGroup?: string;
    updatedAt?: string;
  }

  export interface ActionCandidateBatchValidationResult {
    results?: ActionCandidateValidationResult[];
  }

  export interface ActionExecution {
    actionId: string;
    actionType: string;
    actorId?: string;
    actorName?: string;
    actorType?: ActionActorType | string;
    auditLevel?: ActionAuditLevel | string;
    completedAt?: string;
    correlationId?: string;
    createdAt?: string;
    errorSummary?: string;
    executionMode?: ActionExecutionMode | string;
    idempotencyKey?: string;
    ownerExecutionRef?: string;
    ownerService?: string;
    payloadSummary?: string;
    requestId?: string;
    resultSummary?: string;
    retryable?: boolean;
    source?: ActionSource | string;
    status?: ActionStatus | string;
    targetId?: string;
    targetOwnerService?: string;
    targetType?: string;
    tenantId?: string;
    traceId?: string;
    updatedAt?: string;
  }

  export interface ActionEvent {
    actionId: string;
    eventDataJson?: string;
    eventId: string;
    eventType: string;
    message?: string;
    occurredAt?: string;
    sequenceNo?: number;
    status?: ActionStatus | string;
    tenantId?: string;
    traceId?: string;
  }

  export interface ActionQueryParams {
    actionType?: string;
    actorId?: string;
    actorType?: string;
    correlationId?: string;
    idempotencyKey?: string;
    ownerService?: string;
    page?: number;
    size?: number;
    source?: string;
    status?: string;
    targetId?: string;
    targetType?: string;
    tenantId?: string;
    traceId?: string;
  }

  export interface PageResult<T> {
    records?: T[];
    total: number;
    [key: string]: unknown;
  }

  export interface SubscribeActionEventsOptions {
    actionType?: string;
    onEnd?: () => void;
    onEvent?: (event: ActionEvent) => void;
    onRawMessage?: (message: string) => void;
    ownerService?: string;
    signal?: AbortSignal;
    target?: ActionTarget;
  }

  export interface ActionRouteHint {
    actionType?: string;
    ownerService?: string;
    target?: ActionTarget;
  }
}

export const ACTION_TYPES = {
  integrationCallbackSignal: 'integration.callback.signal',
  integrationInvocationStateChanging: 'integration.invocation.stateChanging',
  integrationOrchestrationStart: 'integration.orchestration.start',
  lowcodeFormCreate: 'lowcode.form.create',
  lowcodeFormSave: 'lowcode.form.save',
  lowcodeFormSubmit: 'lowcode.form.submit',
  lowcodeWorkflowRetry: 'lowcode.workflow.retry',
  processClosureEffectMarkHandled: 'process.closure.effect.markHandled',
  processClosureEffectRetry: 'process.closure.effect.retry',
  processInstanceStart: 'process.instance.start',
  processTaskAddSign: 'process.task.addSign',
  processTaskApprove: 'process.task.approve',
  processTaskReject: 'process.task.reject',
  processTaskTransfer: 'process.task.transfer',
} as const;

export const ACTION_TARGET_TYPES = {
  integrationRoute: 'INTEGRATION_ROUTE',
  lowcodeForm: 'LOWCODE_FORM',
  openApiCallbackInbox: 'OPENAPI_CALLBACK_INBOX',
  processClosureEffect: 'PROCESS_CLOSURE_EFFECT',
  processInstance: 'PROCESS_INSTANCE',
  processTask: 'PROCESS_TASK',
} as const;

const OWNER_ACTION_BASE_PATHS: Record<string, string> = {
  'service-lowcode': '/lowcode-runtime/actions',
  'service-openapi': '/openapi/management/actions',
  'service-workflow-engine': '/workflow-actions',
};

type ActionRouteSource = {
  actionType?: string;
  ownerService?: string;
  target?: ActionApi.ActionTarget;
};

function resolveOwnerService(data: ActionRouteSource) {
  const owner = data.ownerService ?? data.target?.ownerService;
  if (owner && OWNER_ACTION_BASE_PATHS[owner]) {
    return owner;
  }
  const actionType = data.actionType;
  if (!actionType) {
    return undefined;
  }
  if (actionType.startsWith('lowcode.')) {
    return 'service-lowcode';
  }
  if (actionType.startsWith('process.')) {
    return 'service-workflow-engine';
  }
  if (actionType.startsWith('integration.')) {
    return 'service-openapi';
  }
  return undefined;
}

function actionBasePath(data: ActionRouteSource) {
  const owner = resolveOwnerService(data);
  return owner ? (OWNER_ACTION_BASE_PATHS[owner] ?? '/actions') : '/actions';
}

function actionReadBasePath(options?: ActionApi.ActionRouteHint) {
  return options ? actionBasePath(options) : '/actions';
}

function actionSubmitPath(data: ActionApi.GlobalActionRequest) {
  const basePath = actionBasePath(data);
  return basePath === '/actions' ? '/actions' : `${basePath}/dispatch`;
}

async function submitAction<TData = Record<string, unknown>>(
  data: ActionApi.GlobalActionRequest,
) {
  const result = await requestClient.post<ActionApi.GlobalActionResult<TData>>(
    actionSubmitPath(data),
    data,
  );
  rememberActionOwner(result, data);
  return result;
}

async function getActionDetail(
  actionId: string,
  options?: ActionApi.ActionRouteHint,
) {
  const basePath = actionReadBasePath(options);
  return requestClient.get<ActionApi.ActionExecution>(`${basePath}/${actionId}`);
}

async function getActionEvents(
  actionId: string,
  options?: ActionApi.ActionRouteHint,
) {
  const basePath = actionReadBasePath(options);
  return requestClient.get<ActionApi.ActionEvent[]>(
    `${basePath}/${actionId}/events`,
  );
}

async function queryActions(params: ActionApi.ActionQueryParams) {
  return requestClient.get<ActionApi.PageResult<ActionApi.ActionExecution>>(
    '/actions',
    { params },
  );
}

async function validateActionCandidate(data: ActionApi.ActionCandidate) {
  return requestClient.post<ActionApi.ActionCandidateValidationResult>(
    `${actionBasePath(data)}/candidates/validate`,
    data,
  );
}

async function validateActionCandidates(data: ActionApi.ActionCandidate[]) {
  const groups = new Map<
    string,
    Array<{ candidate: ActionApi.ActionCandidate; index: number }>
  >();
  data.forEach((candidate, index) => {
    const basePath = actionBasePath(candidate);
    const group = groups.get(basePath) ?? [];
    group.push({ candidate, index });
    groups.set(basePath, group);
  });
  const ordered: ActionApi.ActionCandidateValidationResult[] = [];
  await Promise.all(
    Array.from(groups.entries()).map(async ([basePath, group]) => {
      const result = await requestClient.post<ActionApi.ActionCandidateBatchValidationResult>(
        `${basePath}/candidates/batch-validate`,
        { candidates: group.map((item) => item.candidate) },
      );
      const results = result.results ?? [];
      group.forEach((item, groupIndex) => {
        ordered[item.index] = results[groupIndex] ?? {
          actionType: item.candidate.actionType,
          enabled: false,
          visible: false,
        };
      });
    }),
  );
  return ordered;
}

async function dispatchActionCandidate<TData = Record<string, unknown>>(
  data: ActionApi.ActionCandidate,
) {
  const result = await requestClient.post<ActionApi.GlobalActionResult<TData>>(
    `${actionBasePath(data)}/candidates/dispatch`,
    data,
  );
  rememberActionOwner(result, data);
  return result;
}

async function subscribeActionEvents(
  actionId: string,
  options: ActionApi.SubscribeActionEventsOptions = {},
) {
  let buffer = '';
  const basePath = actionReadBasePath(options);
  await requestClient.requestSSE(`${basePath}/${actionId}/stream`, undefined, {
    method: 'GET',
    onEnd: options.onEnd,
    onMessage(message: string) {
      options.onRawMessage?.(message);
      buffer += message;
      const chunks = buffer.split(/\r?\n\r?\n/);
      buffer = chunks.pop() ?? '';
      chunks.forEach((chunk) => {
        const event = parseSseActionEvent(chunk);
        if (event) {
          options.onEvent?.(event);
        }
      });
    },
    signal: options.signal,
  });
}

const actionOwnerById = new Map<string, string>();

function rememberActionOwner<TData>(
  result: ActionApi.GlobalActionResult<TData> | undefined,
  source: ActionApi.ActionCandidate | ActionApi.GlobalActionRequest,
) {
  if (!result?.actionId) {
    return;
  }
  const owner = result.ownerService ?? resolveOwnerService(source);
  if (owner) {
    actionOwnerById.set(result.actionId, owner);
  }
}

function parseSseActionEvent(chunk: string) {
  const dataLines = chunk
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart());
  if (dataLines.length === 0) {
    return undefined;
  }
  try {
    return JSON.parse(dataLines.join('\n')) as ActionApi.ActionEvent;
  } catch {
    return undefined;
  }
}

function createActionIdempotencyKey(
  scope: string,
  ...parts: Array<number | string | undefined>
) {
  const normalizedParts = parts
    .filter((part) => part !== undefined && String(part).trim() !== '')
    .map((part) => String(part).trim());
  const unique =
    globalThis.crypto?.randomUUID?.() ??
    `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`;
  return [scope, ...normalizedParts, unique].join(':');
}

export {
  createActionIdempotencyKey,
  dispatchActionCandidate,
  getActionDetail,
  getActionEvents,
  queryActions,
  submitAction,
  subscribeActionEvents,
  validateActionCandidate,
  validateActionCandidates,
};
