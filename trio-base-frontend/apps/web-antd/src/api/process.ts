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
  formData?: Record<string, any>;
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

export {
  addSignTask,
  approveTask,
  createProcessPackage,
  createProcessPackageVersion,
  deleteProcessPackage,
  getMyCompletedTasks,
  getMyPendingTasks,
  getProcessInstanceById,
  getProcessHistory,
  getProcessInstanceList,
  getProcessPackageById,
  getProcessPackageByKey,
  getProcessPackageList,
  getTaskById,
  getRejectTargets,
  offlineProcessPackage,
  publishProcessPackage,
  rejectTask,
  startProcessInstance,
  transferTask,
  updateProcessPackage,
};
