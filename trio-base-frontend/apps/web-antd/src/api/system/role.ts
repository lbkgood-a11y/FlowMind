import { requestClient } from '#/api/request';

export namespace SystemRoleApi {
  export interface SystemRole {
    description?: string;
    id: string;
    roleCode: string;
    roleName: string;
  }
}

async function getRoleList() {
  return requestClient.get<SystemRoleApi.SystemRole[]>('/roles');
}

export { getRoleList };
