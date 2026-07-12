import type { Recordable } from '@vben/types';

import { requestClient } from '#/api/request';

export namespace SystemUserApi {
  export interface SystemUser {
    createdAt?: string;
    email?: string;
    id: string;
    phone?: string;
    roles?: string[];
    status: 0 | 1;
    username: string;
  }

  export interface UserListResult {
    items: SystemUser[];
    total: number;
  }

  export interface SaveUserParams {
    email?: string;
    password?: string;
    phone?: string;
    roleIds?: string[];
    status?: 0 | 1;
    username?: string;
  }
}

async function getUserList(params: Recordable<any>) {
  const page = await requestClient.get<{
    page: number;
    records: SystemUserApi.SystemUser[];
    size: number;
    total: number;
  }>('/users', {
    params: {
      createdEnd: params.createdEnd,
      createdStart: params.createdStart,
      keyword: params.keyword,
      page: params.page ?? params.currentPage ?? 1,
      size: params.size ?? params.pageSize ?? 20,
      status: params.status,
      userId: params.userId,
      username: params.username,
    },
  });

  return {
    items: page.records,
    total: page.total,
  } satisfies SystemUserApi.UserListResult;
}

async function getUserById(id: string) {
  return requestClient.get<SystemUserApi.SystemUser>(`/users/${id}`);
}

async function createUser(data: SystemUserApi.SaveUserParams) {
  return requestClient.post<SystemUserApi.SystemUser>('/users', data);
}

async function updateUser(id: string, data: SystemUserApi.SaveUserParams) {
  return requestClient.put<SystemUserApi.SystemUser>(`/users/${id}`, data);
}

async function deleteUser(id: string) {
  return requestClient.delete(`/users/${id}`);
}

async function updateUserStatus(id: string, status: 0 | 1) {
  return requestClient.put(`/users/${id}/status`, undefined, {
    params: { status },
  });
}

async function assignUserRoles(id: string, roleIds: string[]) {
  return requestClient.post(`/users/${id}/roles`, roleIds);
}

export {
  assignUserRoles,
  createUser,
  deleteUser,
  getUserById,
  getUserList,
  updateUser,
  updateUserStatus,
};
