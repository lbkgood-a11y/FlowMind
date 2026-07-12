import { requestClient } from '#/api/request';

export namespace SystemOrgApi {
  export interface OrgDimension {
    dimensionCode: string;
    dimensionName: string;
    id: string;
    isDefault?: 0 | 1;
    sortOrder?: number;
    status?: 0 | 1;
  }

  export interface OrgTreeNode {
    children?: OrgTreeNode[];
    description?: string;
    dimensionId: string;
    id: string;
    level?: number;
    parentUnitId?: string;
    relationId: string;
    sortOrder?: number;
    status?: 0 | 1;
    treePath?: string;
    unitCode: string;
    unitName: string;
    unitType?: string;
  }

  export interface OrgUnit {
    description?: string;
    id: string;
    parentId?: string;
    sortOrder?: number;
    status?: 0 | 1;
    treePath?: string;
    unitCode: string;
    unitName: string;
    unitType?: string;
  }

  export interface SaveOrgUnitParams {
    description?: string;
    dimensionCode?: string;
    enabled?: boolean;
    parentId?: string;
    sortOrder?: number;
    unitCode?: string;
    unitName: string;
    unitType?: string;
  }

  export interface SaveOrgRelationParams {
    enabled?: boolean;
    parentUnitId?: string;
    sortOrder?: number;
  }

  export interface UserOrgAssignment {
    effectiveFrom?: string;
    effectiveTo?: string;
    leader?: boolean;
    orgUnitId: string;
    positionId?: string;
    positionName?: string;
    primary?: boolean;
    status?: 0 | 1;
  }

  export interface UserOrgAssignmentResponse extends UserOrgAssignment {
    dimensionCode?: string;
    dimensionId?: string;
    id: string;
    orgUnitName?: string;
    userId: string;
  }
}

async function getOrgDimensions() {
  return requestClient.get<SystemOrgApi.OrgDimension[]>('/org/dimensions');
}

async function getOrgUnits(params?: {
  keyword?: string;
  status?: 0 | 1;
  unitType?: string;
}) {
  return requestClient.get<SystemOrgApi.OrgUnit[]>('/org/units', { params });
}

async function getOrgTree(dimensionCode: string) {
  return requestClient.get<SystemOrgApi.OrgTreeNode[]>(
    `/org/dimensions/${dimensionCode}/tree`,
  );
}

async function createOrgUnit(data: SystemOrgApi.SaveOrgUnitParams) {
  return requestClient.post<SystemOrgApi.OrgUnit>('/org/units', data);
}

async function updateOrgUnit(
  id: string,
  data: SystemOrgApi.SaveOrgUnitParams,
) {
  return requestClient.put<SystemOrgApi.OrgUnit>(`/org/units/${id}`, data);
}

async function deleteOrgUnit(id: string) {
  return requestClient.delete(`/org/units/${id}`);
}

async function saveOrgRelation(
  dimensionCode: string,
  childUnitId: string,
  data: SystemOrgApi.SaveOrgRelationParams,
) {
  return requestClient.put(
    `/org/dimensions/${dimensionCode}/relations/${childUnitId}`,
    data,
  );
}

async function deleteOrgRelation(dimensionCode: string, childUnitId: string) {
  return requestClient.delete(
    `/org/dimensions/${dimensionCode}/relations/${childUnitId}`,
  );
}

async function getUserOrgAssignments(userId: string, dimensionCode?: string) {
  return requestClient.get<SystemOrgApi.UserOrgAssignmentResponse[]>(
    `/org/users/${userId}/units`,
    { params: { dimensionCode } },
  );
}

async function assignUserOrgUnits(
  userId: string,
  data: {
    assignments: SystemOrgApi.UserOrgAssignment[];
    dimensionCode: string;
    primaryOrgUnitId?: string;
  },
) {
  return requestClient.put(`/org/users/${userId}/units`, data);
}

export {
  assignUserOrgUnits,
  createOrgUnit,
  deleteOrgRelation,
  deleteOrgUnit,
  getOrgDimensions,
  getOrgTree,
  getOrgUnits,
  getUserOrgAssignments,
  saveOrgRelation,
  updateOrgUnit,
};
