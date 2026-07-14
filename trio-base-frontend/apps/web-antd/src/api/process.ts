import type { Recordable } from '@vben/types';

import { requestClient } from '#/api/request';

// ── 流程包 ──
export namespace ProcessApi {
  export interface ProcessPackage {
    id: string;
    processKey: string;
    name: string;
    category: string;
    description?: string;
    version: number;
    status: string; // DRAFT / PUBLISHED / OFFLINE
    processJson: string;
    formSchema?: string;
    formUiSchema?: string;
    formDefinitionId?: string;
    formDefinitionVersion?: number;
    agentFollowUpPlanJson?: string;
    businessBindingSnapshot?: string;
    closurePlanJson?: string;
    launchPlanJson?: string;
    permissionPlanJson?: string;
    sourcePackageId?: string;
    publishedAt?: string;
    createdAt: string;
    updatedAt: string;
  }

  export interface ProcessInstance {
    id: string;
    processPackageId: string;
    processKey: string;
    processName: string;
    version: number;
    title?: string;
    status: string;
    formData?: string;
    businessId?: string;
    businessType?: string;
    launchIdempotencyKey?: string;
    launchMode?: string;
    tenantId?: string;
    initiatorId: string;
    initiatorName: string;
    currentNodeId?: string;
    startedAt: string;
    completedAt?: string;
    createdAt: string;
  }

  export interface TaskItem {
    id: string;
    processInstanceId: string;
    processKey: string;
    processName: string;
    nodeId: string;
    nodeName: string;
    nodeType: string;
    title?: string;
    status: string;
    assigneeId?: string;
    assigneeName?: string;
    assigneeType: string;
    nodeVisitNo?: number;
    sourceTaskId?: string;
    rootTaskId?: string;
    comment?: string;
    claimedAt?: string;
    completedAt?: string;
    createdAt: string;
  }

  export interface PageListResult<T> {
    items: T[];
    total: number;
  }

  export interface NodeHistoryItem {
    id: string;
    nodeId: string;
    nodeName: string;
    nodeType: string;
    visitNo: number;
    status: string;
    assigneeSnapshot?: string;
    result?: string;
    enteredAt: string;
    exitedAt?: string;
  }

  export interface TaskOperationItem {
    operationId: string;
    sourceTaskId: string;
    targetTaskId?: string;
    action: string;
    operatorId: string;
    operatorName?: string;
    targetUserId?: string;
    targetUserName?: string;
    targetNodeId?: string;
    comment?: string;
    status: string;
    traceId?: string;
    resultJson?: string;
    createdAt: string;
  }

  export interface ProcessHistory {
    nodes: NodeHistoryItem[];
    operations: TaskOperationItem[];
  }

  export interface FormFieldError {
    field: string;
    code: string;
    message: string;
    keyword: string;
  }

  export interface BusinessObjectSummary {
    description?: string;
    displayName: string;
    id: string;
    serviceCode: string;
    status: string;
    tenantId: string;
    typeCode: string;
    version: number;
  }

  export interface BusinessObjectStatus {
    displayName: string;
    initial?: boolean;
    sortOrder?: number;
    statusCode: string;
    statusGroup?: string;
    terminal?: boolean;
  }

  export interface BusinessObjectForm {
    displayName: string;
    formDefinitionId?: string;
    formKey: string;
    formRole: string;
    formVersion?: number;
    required?: boolean;
    sortOrder?: number;
  }

  export interface BusinessObjectPermission {
    actionCode: string;
    actionGroup?: string;
    displayName: string;
    permissionCode: string;
    sortOrder?: number;
  }

  export interface BusinessObjectAction {
    actionCode: string;
    actionType: string;
    displayName: string;
    modeDefault?: string;
    paramSchemaJson?: string;
    permissionAction?: string;
    sortOrder?: number;
  }

  export interface BusinessObjectEvent {
    displayName: string;
    eventCode: string;
    eventType: string;
    payloadSchemaJson?: string;
    sortOrder?: number;
  }

  export interface BusinessObjectAgentAction {
    agentActionCode: string;
    displayName: string;
    modeDefault?: string;
    paramSchemaJson?: string;
    permissionAction?: string;
    resultSchemaJson?: string;
    sortOrder?: number;
  }

  export interface BusinessObjectTemplate {
    configJson: string;
    displayName: string;
    sortOrder?: number;
    templateCode: string;
    templateType: string;
  }

  export interface BusinessObjectCatalog {
    actions: BusinessObjectAction[];
    agentActions: BusinessObjectAgentAction[];
    events: BusinessObjectEvent[];
    forms: BusinessObjectForm[];
    object: BusinessObjectSummary;
    permissions: BusinessObjectPermission[];
    statuses: BusinessObjectStatus[];
    templates: BusinessObjectTemplate[];
  }

  export interface ProcessClosureDetail {
    closure?: {
      businessId?: string;
      businessType?: string;
      closureStatus: string;
      completedAt?: string;
      id: string;
      startedAt?: string;
      traceId?: string;
    };
    effects: Array<{
      attemptCount?: number;
      businessActionCode?: string;
      businessActionName?: string;
      effectKey: string;
      effectType: string;
      executorKey?: string;
      failureCategory?: string;
      id: string;
      idempotencyKey?: string;
      lastError?: string;
      mode: string;
      manualHandlingAvailable?: boolean;
      nextRetryAt?: string;
      requestJson?: string;
      resultJson?: string;
      retryAvailable?: boolean;
      status: string;
      traceId?: string;
      triggerOutcome?: string;
    }>;
    outcome?: {
      businessId?: string;
      businessType?: string;
      createdAt?: string;
      id: string;
      initiatorId?: string;
      lastOperatorId?: string;
      outcomeStatus: string;
      processInstanceId: string;
      processKey: string;
      processVersion: number;
      reason?: string;
      tenantId?: string;
      traceId?: string;
    };
  }
}

// ── 流程包 API ──
async function getProcessPackageList(params: Recordable<any>) {
  const page = await requestClient.get<{
    records: ProcessApi.ProcessPackage[];
    total: number;
  }>('/process-packages', { params });
  return { items: page.records, total: page.total };
}

async function getProcessPackageById(id: string) {
  return requestClient.get<ProcessApi.ProcessPackage>(`/process-packages/${id}`);
}

async function getProcessPackageByKey(processKey: string) {
  return requestClient.get<ProcessApi.ProcessPackage>(`/process-packages/by-key/${processKey}`);
}

async function createProcessPackage(data: {
  processKey: string;
  name: string;
  category?: string;
  description?: string;
  formDefinitionId?: string;
  processJson: string;
}) {
  return requestClient.post<ProcessApi.ProcessPackage>('/process-packages', data);
}

async function updateProcessPackage(
  id: string,
  data: {
    category?: string;
    description?: string;
    formDefinitionId?: string;
    name?: string;
    processJson?: string;
  },
) {
  return requestClient.put<ProcessApi.ProcessPackage>(`/process-packages/${id}`, data);
}

async function createProcessPackageVersion(id: string) {
  return requestClient.post<ProcessApi.ProcessPackage>(`/process-packages/${id}/versions`);
}

async function publishProcessPackage(id: string) {
  return requestClient.put<ProcessApi.ProcessPackage>(`/process-packages/${id}/publish`);
}

async function offlineProcessPackage(id: string) {
  return requestClient.put<ProcessApi.ProcessPackage>(`/process-packages/${id}/offline`);
}

async function deleteProcessPackage(id: string) {
  return requestClient.delete(`/process-packages/${id}`);
}

// ── 业务对象目录 API ──
async function getBusinessObjectCatalogList() {
  return requestClient.get<ProcessApi.BusinessObjectSummary[]>('/process-business-objects');
}

async function getBusinessObjectCatalog(typeCode: string) {
  return requestClient.get<ProcessApi.BusinessObjectCatalog>(
    `/process-business-objects/${typeCode}`,
  );
}

// ── 流程实例 API ──
async function getProcessInstanceList(params: Recordable<any>) {
  const page = await requestClient.get<{
    records: ProcessApi.ProcessInstance[];
    total: number;
  }>('/process-instances', { params });
  return { items: page.records, total: page.total };
}

async function getProcessInstanceById(id: string) {
  return requestClient.get<ProcessApi.ProcessInstance>(`/process-instances/${id}`);
}

async function getProcessHistory(id: string) {
  return requestClient.get<ProcessApi.ProcessHistory>(`/process-instances/${id}/history`);
}

async function startProcessInstance(data: {
  businessId?: string;
  businessType?: string;
  formData?: Record<string, any>;
  idempotencyKey?: string;
  launchMode?: string;
  processKey: string;
  processPackageId?: string;
  title?: string;
  version?: number;
}) {
  return requestClient.post<ProcessApi.ProcessInstance>('/process-instances/start', data);
}

// ── 任务 API ──
async function getMyPendingTasks(params: Recordable<any>) {
  const page = await requestClient.get<{
    records: ProcessApi.TaskItem[];
    total: number;
  }>('/tasks/my-pending', { params });
  return { items: page.records, total: page.total };
}

async function getMyCompletedTasks(params: Recordable<any>) {
  const page = await requestClient.get<{
    records: ProcessApi.TaskItem[];
    total: number;
  }>('/tasks/my-completed', { params });
  return { items: page.records, total: page.total };
}

async function getTaskById(id: string) {
  return requestClient.get<ProcessApi.TaskItem>(`/tasks/${id}`);
}

async function approveTask(id: string, data: { comment?: string; operationId: string }) {
  return requestClient.post<ProcessApi.TaskItem>(`/tasks/${id}/approve`, data);
}

async function rejectTask(
  id: string,
  data: { comment?: string; operationId: string; targetNodeId?: string },
) {
  return requestClient.post<ProcessApi.TaskItem>(`/tasks/${id}/reject`, data);
}

async function transferTask(
  id: string,
  data: { newAssigneeId: string; newAssigneeName?: string; operationId: string },
) {
  return requestClient.post<ProcessApi.TaskItem>(`/tasks/${id}/transfer`, data);
}

async function addSignTask(
  id: string,
  data: { assigneeId: string; assigneeName?: string; operationId: string },
) {
  return requestClient.post<ProcessApi.TaskItem>(`/tasks/${id}/add-sign`, data);
}

async function getRejectTargets(processInstanceId: string) {
  return requestClient.get<string[]>(`/tasks/reject-targets/${processInstanceId}`);
}

// ── 闭环查询与操作 API ──
async function getProcessClosureDetail(processInstanceId: string) {
  return requestClient.get<ProcessApi.ProcessClosureDetail>(
    `/process-closures/instances/${processInstanceId}`,
  );
}

async function retryClosureEffect(effectId: string) {
  return requestClient.post<ProcessApi.ProcessClosureDetail['effects'][number]>(
    `/process-closures/effects/${effectId}/retry`,
  );
}

async function markClosureEffectHandled(effectId: string, data: { reason?: string }) {
  return requestClient.post<ProcessApi.ProcessClosureDetail['effects'][number]>(
    `/process-closures/effects/${effectId}/manual-handled`,
    data,
  );
}

export {
  addSignTask,
  approveTask,
  createProcessPackage,
  createProcessPackageVersion,
  deleteProcessPackage,
  getBusinessObjectCatalog,
  getBusinessObjectCatalogList,
  getMyCompletedTasks,
  getMyPendingTasks,
  getProcessClosureDetail,
  getProcessInstanceById,
  getProcessHistory,
  getProcessInstanceList,
  getProcessPackageById,
  getProcessPackageByKey,
  getProcessPackageList,
  getTaskById,
  getRejectTargets,
  markClosureEffectHandled,
  offlineProcessPackage,
  publishProcessPackage,
  rejectTask,
  retryClosureEffect,
  startProcessInstance,
  transferTask,
  updateProcessPackage,
};
