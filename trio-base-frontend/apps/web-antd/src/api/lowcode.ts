import type { Recordable } from '@vben/types';

import { requestClient } from '#/api/request';

export namespace LowcodeApi {
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

export {
  createFormDefinition,
  getFormDefinitionById,
  getFormDefinitionList,
  publishFormDefinition,
};
