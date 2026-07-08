import { api } from "@/lib/api";
import type { ApiResponse, PageResult } from "@/lib/lowcode";

export interface UserInfoPayload {
  id: string;
  username: string;
  email?: string;
  status: number;
  roles: string[];
  createdAt?: string;
}

export interface RoleInfo {
  id: string;
  roleCode: string;
  roleName: string;
  description?: string;
  createdAt?: string;
}

export interface PermissionInfo {
  id: string;
  resource: string;
  action: string;
  description?: string;
  createdAt?: string;
}

export interface CreateRoleRequest {
  roleCode: string;
  roleName: string;
  description?: string;
  permissionIds?: string[];
}

export interface CreatePermissionRequest {
  resource: string;
  action: string;
  description?: string;
}

export const adminApi = {
  listUsers: async (page = 1, size = 20) => {
    const response = await api.get<ApiResponse<PageResult<UserInfoPayload>>>("/api/v1/users", {
      params: { page: String(page), size: String(size) },
    });
    return response.data;
  },

  updateUserStatus: async (id: string, status: number) => {
    const response = await api.put<ApiResponse<string>>(`/api/v1/users/${id}/status`, undefined, {
      params: { status: String(status) },
    });
    return response.data;
  },

  assignUserRoles: async (id: string, roleIds: string[]) => {
    const response = await api.post<ApiResponse<string>>(`/api/v1/users/${id}/roles`, roleIds);
    return response.data;
  },

  listRoles: async () => {
    const response = await api.get<ApiResponse<RoleInfo[]>>("/api/v1/roles");
    return response.data;
  },

  createRole: async (payload: CreateRoleRequest) => {
    const query = new URLSearchParams({
      roleCode: payload.roleCode,
      roleName: payload.roleName,
    });
    if (payload.description) {
      query.set("description", payload.description);
    }
    const response = await api.post<ApiResponse<RoleInfo>>(`/api/v1/roles?${query.toString()}`, payload.permissionIds || []);
    return response.data;
  },

  deleteRole: async (id: string) => {
    const response = await api.delete<ApiResponse<string>>(`/api/v1/roles/${id}`);
    return response.data;
  },

  listPermissions: async () => {
    const response = await api.get<ApiResponse<PermissionInfo[]>>("/api/v1/permissions");
    return response.data;
  },

  createPermission: async (payload: CreatePermissionRequest) => {
    const response = await api.post<ApiResponse<PermissionInfo>>("/api/v1/permissions", payload);
    return response.data;
  },

  deletePermission: async (id: string) => {
    const response = await api.delete<ApiResponse<string>>(`/api/v1/permissions/${id}`);
    return response.data;
  },
};
