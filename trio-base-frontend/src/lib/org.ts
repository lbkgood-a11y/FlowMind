import { api } from "@/lib/api";
import type { ApiResponse } from "@/lib/lowcode";

export interface OrgUnitInfo {
  id: string;
  parentId?: string;
  unitCode: string;
  unitName: string;
  treePath?: string;
  sortOrder: number;
  status: number;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface UserOrgRelation {
  id: string;
  userId: string;
  orgUnitId: string;
  createdAt?: string;
}

export interface CreateOrgUnitRequest {
  parentId?: string;
  unitCode: string;
  unitName: string;
  sortOrder?: number;
  enabled?: boolean;
  description?: string;
}

export const orgApi = {
  listOrgUnits: async () => {
    const response = await api.get<ApiResponse<OrgUnitInfo[]>>("/api/v1/org/units");
    return response.data;
  },

  createOrgUnit: async (payload: CreateOrgUnitRequest) => {
    const response = await api.post<ApiResponse<OrgUnitInfo>>("/api/v1/org/units", payload);
    return response.data;
  },

  deleteOrgUnit: async (id: string) => {
    const response = await api.delete<ApiResponse<string>>(`/api/v1/org/units/${id}`);
    return response.data;
  },

  listUserOrgRelations: async () => {
    const response = await api.get<ApiResponse<UserOrgRelation[]>>("/api/v1/org/user-units");
    return response.data;
  },

  assignUserOrgUnits: async (userId: string, orgUnitIds: string[]) => {
    const response = await api.put<ApiResponse<string>>(`/api/v1/org/users/${userId}/units`, {
      orgUnitIds,
    });
    return response.data;
  },
};
