import { requestClient } from '#/api/request';

export namespace SystemRoleApi {
  export interface RoleDetail extends SystemRole {
    permissionIds: string[];
  }

  export interface RoleListParams {
    createdEnd?: string;
    createdStart?: string;
    keyword?: string;
    page?: number;
    roleCode?: string;
    roleName?: string;
    size?: number;
    status?: 0 | 1;
  }

  export interface RoleListResult {
    items: SystemRole[];
    total: number;
  }

  export interface SaveRoleParams {
    description?: string;
    permissionIds?: string[];
    roleCode?: string;
    roleName: string;
    status?: 0 | 1;
  }

  export interface SystemRole {
    createdAt?: string;
    description?: string;
    id: string;
    roleCode: string;
    roleName: string;
    status?: 0 | 1;
  }
}

async function getRoleList(params?: { keyword?: string; status?: 0 | 1 }) {
  return requestClient.get<SystemRoleApi.SystemRole[]>('/roles', { params });
}

async function getRolePage(params: SystemRoleApi.RoleListParams) {
  const page = await requestClient.get<{
    page: number;
    records: SystemRoleApi.SystemRole[];
    size: number;
    total: number;
  }>('/roles/page', {
    params: {
      createdEnd: params.createdEnd,
      createdStart: params.createdStart,
      keyword: params.keyword,
      page: params.page ?? 1,
      roleCode: params.roleCode,
      roleName: params.roleName,
      size: params.size ?? 20,
      status: params.status,
    },
  });

  return {
    items: page.records,
    total: page.total,
  } satisfies SystemRoleApi.RoleListResult;
}

async function getRoleDetail(id: string) {
  return requestClient.get<SystemRoleApi.RoleDetail>(`/roles/${id}`);
}

async function createRole(data: SystemRoleApi.SaveRoleParams) {
  return requestClient.post<SystemRoleApi.SystemRole>('/roles', data);
}

async function updateRole(id: string, data: SystemRoleApi.SaveRoleParams) {
  return requestClient.put<SystemRoleApi.SystemRole>(`/roles/${id}`, data);
}

async function updateRoleStatus(id: string, status: 0 | 1) {
  return requestClient.put<SystemRoleApi.SystemRole>(
    `/roles/${id}/status`,
    undefined,
    {
      params: { status },
    },
  );
}

async function deleteRole(id: string) {
  return requestClient.delete(`/roles/${id}`);
}

async function roleCodeExists(roleCode: string, excludeId?: string) {
  return requestClient.get<boolean>('/roles/exists/code', {
    params: { excludeId, roleCode },
  });
}

export {
  createRole,
  deleteRole,
  getRoleDetail,
  getRoleList,
  getRolePage,
  roleCodeExists,
  updateRole,
  updateRoleStatus,
};
