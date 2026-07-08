import { api } from "@/lib/api";
import type { ApiResponse, PageResult } from "@/lib/lowcode";

export interface UserInfoPayload {
  id: string;
  username: string;
  email?: string;
  phone?: string;
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

export interface RoleDetailInfo extends RoleInfo {
  permissionIds: string[];
}

export interface PermissionInfo {
  id: string;
  resource: string;
  action: string;
  description?: string;
  createdAt?: string;
}

export interface MenuInfo {
  id: string;
  parentId?: string;
  menuKey: string;
  menuName: string;
  path: string;
  icon?: string;
  menuGroup: string;
  sortOrder: number;
  visible: number;
  permissionId?: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateRoleRequest {
  roleCode: string;
  roleName: string;
  description?: string;
  permissionIds?: string[];
}

export interface UpdateRoleRequest {
  roleName: string;
  description?: string;
  permissionIds?: string[];
}

export interface CreatePermissionRequest {
  resource: string;
  action: string;
  description?: string;
}

export interface CreateMenuRequest {
  parentId?: string;
  menuKey: string;
  menuName: string;
  path: string;
  icon?: string;
  menuGroup: string;
  sortOrder?: number;
  visible?: boolean;
  permissionId?: string;
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

  getRoleDetail: async (id: string) => {
    const response = await api.get<ApiResponse<RoleDetailInfo>>(`/api/v1/roles/${id}`);
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

  updateRole: async (id: string, payload: UpdateRoleRequest) => {
    const response = await api.put<ApiResponse<RoleInfo>>(`/api/v1/roles/${id}`, payload);
    return response.data;
  },

  listPermissions: async () => {
    const response = await api.get<ApiResponse<PermissionInfo[]>>("/api/v1/permissions");
    return response.data;
  },

  listPermissionsPage: async (page = 1, size = 20) => {
    const response = await api.get<ApiResponse<PageResult<PermissionInfo>>>("/api/v1/permissions/page", {
      params: { page: String(page), size: String(size) },
    });
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

  listMenus: async () => {
    const response = await api.get<ApiResponse<MenuInfo[]>>("/api/v1/menus");
    return response.data;
  },

  createMenu: async (payload: CreateMenuRequest) => {
    const response = await api.post<ApiResponse<MenuInfo>>("/api/v1/menus", payload);
    return response.data;
  },

  deleteMenu: async (id: string) => {
    const response = await api.delete<ApiResponse<string>>(`/api/v1/menus/${id}`);
    return response.data;
  },
};
