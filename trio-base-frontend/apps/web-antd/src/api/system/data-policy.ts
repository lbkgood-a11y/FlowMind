import { requestClient } from '#/api/request';

export namespace SystemDataPolicyApi {
  export interface DataPolicyDimension {
    dimensionCode: string;
    orgUnitIds?: string[];
    scopeType: string;
    sortOrder?: number;
  }

  export interface DataPolicy extends SaveDataPolicyParams {
    createdAt?: string;
    id: string;
    status?: 0 | 1;
  }

  export interface SaveDataPolicyParams {
    actionCode: string;
    combineMode: 'AND' | 'OR';
    description?: string;
    dimensions: DataPolicyDimension[];
    effect: 'ALLOW' | 'DENY';
    resourceCode: string;
    roleId: string;
    status?: 0 | 1;
  }

  export interface EffectiveDataPolicy {
    actionCode: string;
    orgContextResolved: boolean;
    policies: DataPolicy[];
    resourceCode: string;
    restrictive: boolean;
    roleIds: string[];
    userId: string;
  }
}

async function getDataPolicies(roleId?: string) {
  return requestClient.get<SystemDataPolicyApi.DataPolicy[]>('/data-policies', {
    params: { roleId },
  });
}

async function getDataPolicyDetail(id: string) {
  return requestClient.get<SystemDataPolicyApi.DataPolicy>(
    `/data-policies/${id}`,
  );
}

async function createDataPolicy(data: SystemDataPolicyApi.SaveDataPolicyParams) {
  return requestClient.post<SystemDataPolicyApi.DataPolicy>(
    '/data-policies',
    data,
  );
}

async function updateDataPolicy(
  id: string,
  data: SystemDataPolicyApi.SaveDataPolicyParams,
) {
  return requestClient.put<SystemDataPolicyApi.DataPolicy>(
    `/data-policies/${id}`,
    data,
  );
}

async function deleteDataPolicy(id: string) {
  return requestClient.delete(`/data-policies/${id}`);
}

async function getEffectiveDataPolicy(params: {
  actionCode: string;
  resourceCode: string;
  userId: string;
}) {
  return requestClient.get<SystemDataPolicyApi.EffectiveDataPolicy>(
    '/data-policies/effective',
    { params },
  );
}

export {
  createDataPolicy,
  deleteDataPolicy,
  getDataPolicies,
  getDataPolicyDetail,
  getEffectiveDataPolicy,
  updateDataPolicy,
};
