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
    comment?: string;
    claimedAt?: string;
    completedAt?: string;
    createdAt: string;
  }

  export interface PageListResult<T> {
    items: T[];
    total: number;
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
  processJson?: string;
}) {
  return requestClient.post<ProcessApi.ProcessPackage>('/process-packages', data);
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

async function startProcessInstance(data: { processKey: string; title?: string; formData?: Record<string, any> }) {
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

async function approveTask(id: string, data: { action: string; comment?: string }) {
  return requestClient.post<ProcessApi.TaskItem>(`/tasks/${id}/approve`, data);
}

export {
  approveTask,
  createProcessPackage,
  deleteProcessPackage,
  getMyCompletedTasks,
  getMyPendingTasks,
  getProcessInstanceById,
  getProcessInstanceList,
  getProcessPackageById,
  getProcessPackageByKey,
  getProcessPackageList,
  getTaskById,
  offlineProcessPackage,
  publishProcessPackage,
  startProcessInstance,
};
