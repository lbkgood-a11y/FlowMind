import type { Recordable } from '@vben/types';

import { requestClient } from '#/api/request';

export namespace LowcodeApi {
  export interface FormDataResource {
    businessObjectId: string;
    formKey: string;
    resourceCode: string;
    resourceName: string;
    resourceType: 'LOWCODE_FORM';
    version: number;
  }

  export interface FormFieldSchema {
    defaultValue?: string;
    fieldKey: string;
    fieldType?: string;
    label: string;
    optionsJson?: string;
    placeholder?: string;
    required?: boolean;
    sortOrder?: number;
  }

  export interface FormDefinition {
    id: string;
    tenantId?: string;
    formKey: string;
    name: string;
    description?: string;
    version: number;
    status: string;
    schemaHash?: string;
    schemaJson?: string;
    uiSchemaJson?: string;
    publishedAt?: string;
    offlineAt?: string;
    createdBy?: string;
    createdAt: string;
    fields?: FormFieldSchema[];
  }

  export interface FormInstance {
    dataJson: string;
    formDefinitionId: string;
    formDefinitionVersion?: number;
    formKey: string;
    id: string;
    processBindingTraceId?: string;
    processInstanceId?: string;
    processKey?: string;
    schemaHash?: string;
    status: string;
    submittedAt: string;
    submittedBy: string;
    tenantId?: string;
    workflowBoundAt?: string;
    workflowStatus?: string;
    workflowStatusUpdatedAt?: string;
  }

  export interface PageListResult<T> {
    items: T[];
    total: number;
  }

  export interface ApplicationPage {
    id: string;
    metadataJson: string;
    pageType: 'CREATE' | 'DETAIL' | 'LIST' | string;
    sortOrder?: number;
  }

  export interface ApplicationAction {
    actionCode: string;
    actionType:
      | 'CREATE'
      | 'OPEN_DETAIL'
      | 'OPEN_PROCESS'
      | 'RETRY_WORKFLOW'
      | 'SAVE'
      | 'SUBMIT'
      | 'SUBMIT_AND_LAUNCH_WORKFLOW'
      | string;
    formDefinitionId?: string;
    id: string;
    label: string;
    metadataJson?: string;
    permissionCode?: string;
    processKey?: string;
    sortOrder?: number;
    status?: string;
  }

  export interface RuntimeApplicationSummary {
    appKey: string;
    description?: string;
    formKey: string;
    formVersion?: number;
    metadataHash?: string;
    name: string;
    publishedAt?: string;
    schemaHash?: string;
    tenantId?: string;
    version: number;
    versionId: string;
  }

  export interface RuntimeApplicationDescriptor
    extends RuntimeApplicationSummary {
    actions: ApplicationAction[];
    pages: ApplicationPage[];
    primaryFormDefinitionId: string;
    schemaJson: string;
    uiSchemaJson?: string;
  }

  export interface RuntimeWorkflow {
    processInstanceId?: string;
    processKey?: string;
    processPackageId?: string;
    status?: string;
    version?: number;
  }

  export interface RuntimeActionResponse {
    actionCode: string;
    errorCode?: string;
    formInstance?: FormInstance;
    message?: string;
    retryable?: boolean;
    status: 'FORM_SAVED' | 'WORKFLOW_PENDING' | 'WORKFLOW_STARTED' | string;
    workflow?: RuntimeWorkflow;
  }
}

async function getFormDefinitionList(params: Recordable<any>) {
  const page = await requestClient.get<{
    records: LowcodeApi.FormDefinition[];
    total: number;
  }>('/forms', { params });
  return { items: page.records, total: page.total };
}

async function getFormDefinitionById(id: string) {
  return requestClient.get<LowcodeApi.FormDefinition>(`/forms/${id}`);
}

async function getFormDataResources() {
  return requestClient.get<LowcodeApi.FormDataResource[]>(
    '/forms/data-resources',
  );
}

async function createFormDefinition(data: {
  description?: string;
  fields?: LowcodeApi.FormFieldSchema[];
  formKey: string;
  name: string;
  schemaJson?: string;
  uiSchemaJson?: string;
}) {
  return requestClient.post<LowcodeApi.FormDefinition>('/forms', data);
}

async function updateFormDefinition(
  id: string,
  data: {
    description?: string;
    fields?: LowcodeApi.FormFieldSchema[];
    name?: string;
    schemaJson?: string;
    uiSchemaJson?: string;
  },
) {
  return requestClient.put<LowcodeApi.FormDefinition>(`/forms/${id}`, data);
}

async function deriveFormDefinitionVersion(id: string) {
  return requestClient.post<LowcodeApi.FormDefinition>(`/forms/${id}/versions`);
}

async function getFormDefinitionVersions(formKey: string) {
  return requestClient.get<LowcodeApi.FormDefinition[]>(
    `/forms/${formKey}/versions`,
  );
}

async function publishFormDefinition(id: string) {
  return requestClient.put<LowcodeApi.FormDefinition>(`/forms/${id}/publish`);
}

async function offlineFormDefinition(id: string) {
  return requestClient.put<LowcodeApi.FormDefinition>(`/forms/${id}/offline`);
}

async function getFormInstanceList(formKey: string, params: Recordable<any>) {
  const page = await requestClient.get<{
    records: LowcodeApi.FormInstance[];
    total: number;
  }>(`/forms/${formKey}/instances`, { params });
  return { items: page.records, total: page.total };
}

async function submitFormInstance(
  formKey: string,
  data: Record<string, unknown>,
) {
  return requestClient.post<LowcodeApi.FormInstance>(`/forms/${formKey}/submit`, {
    data,
  });
}

async function bindFormInstanceProcess(
  formKey: string,
  instanceId: string,
  data: {
    processInstanceId: string;
    processKey: string;
    workflowStatus?: string;
  },
) {
  return requestClient.put<LowcodeApi.FormInstance>(
    `/forms/${formKey}/instances/${instanceId}/process`,
    data,
  );
}

async function getRuntimeApplicationList(params: Recordable<any>) {
  const page = await requestClient.get<{
    records: LowcodeApi.RuntimeApplicationSummary[];
    total: number;
  }>('/lowcode-runtime/apps', { params });
  return { items: page.records, total: page.total };
}

async function getRuntimeApplicationDescriptor(
  appKey: string,
  params?: { version?: number },
) {
  return requestClient.get<LowcodeApi.RuntimeApplicationDescriptor>(
    `/lowcode-runtime/apps/${appKey}`,
    { params },
  );
}

async function getRuntimeApplicationInstances(
  appKey: string,
  params: Recordable<any>,
) {
  const page = await requestClient.get<{
    records: LowcodeApi.FormInstance[];
    total: number;
  }>(`/lowcode-runtime/apps/${appKey}/instances`, { params });
  return { items: page.records, total: page.total };
}

async function getRuntimeApplicationInstance(
  appKey: string,
  instanceId: string,
  params?: { version?: number },
) {
  return requestClient.get<LowcodeApi.FormInstance>(
    `/lowcode-runtime/apps/${appKey}/instances/${instanceId}`,
    { params },
  );
}

async function runRuntimeApplicationAction(
  appKey: string,
  actionCode: string,
  data: {
    data?: Record<string, unknown>;
    idempotencyKey?: string;
    title?: string;
  },
  params?: { version?: number },
) {
  return requestClient.post<LowcodeApi.RuntimeActionResponse>(
    `/lowcode-runtime/apps/${appKey}/actions/${actionCode}`,
    data,
    { params },
  );
}

async function retryRuntimeApplicationWorkflow(
  appKey: string,
  instanceId: string,
  data?: {
    actionCode?: string;
    idempotencyKey?: string;
  },
  params?: { version?: number },
) {
  return requestClient.post<LowcodeApi.RuntimeActionResponse>(
    `/lowcode-runtime/apps/${appKey}/instances/${instanceId}/retry-workflow`,
    data,
    { params },
  );
}

export {
  bindFormInstanceProcess,
  createFormDefinition,
  deriveFormDefinitionVersion,
  getFormDataResources,
  getFormDefinitionById,
  getFormDefinitionList,
  getFormDefinitionVersions,
  getFormInstanceList,
  getRuntimeApplicationDescriptor,
  getRuntimeApplicationInstance,
  getRuntimeApplicationInstances,
  getRuntimeApplicationList,
  offlineFormDefinition,
  publishFormDefinition,
  retryRuntimeApplicationWorkflow,
  runRuntimeApplicationAction,
  submitFormInstance,
  updateFormDefinition,
};
