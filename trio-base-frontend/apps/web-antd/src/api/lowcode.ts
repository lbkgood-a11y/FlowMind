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

  export interface FormDefinition {
    id: string;
    formKey: string;
    name: string;
    description?: string;
    version: number;
    status: string;
    schemaJson?: string;
    uiSchemaJson?: string;
    createdBy?: string;
    createdAt: string;
  }

  export interface FormInstance {
    dataJson: string;
    formDefinitionId: string;
    formKey: string;
    id: string;
    processInstanceId?: string;
    processKey?: string;
    status: string;
    submittedAt: string;
    submittedBy: string;
    workflowStatus?: string;
  }

  export interface PageListResult<T> {
    items: T[];
    total: number;
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
  formKey: string;
  name: string;
  schemaJson?: string;
  uiSchemaJson?: string;
}) {
  return requestClient.post<LowcodeApi.FormDefinition>('/forms', data);
}

async function publishFormDefinition(id: string) {
  return requestClient.put<LowcodeApi.FormDefinition>(`/forms/${id}/publish`);
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

export {
  bindFormInstanceProcess,
  createFormDefinition,
  getFormDataResources,
  getFormDefinitionById,
  getFormDefinitionList,
  getFormInstanceList,
  publishFormDefinition,
  submitFormInstance,
};
